package com.kpi.studentcouncil.crm.members.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.org.OrgQueryService;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;

/**
 * Directory search over member profiles. Org-related criteria are resolved to member ids through the public API
 * of the org module only ({@link OrgQueryService}, {@link OrgService}); the result is intersected in SQL.
 */
@Component
class MemberDirectory {

	private static final char LIKE_ESCAPE = '\\';

	private final MemberProfileRepository profiles;
	private final OrgQueryService orgQuery;
	private final OrgService orgUnits;

	MemberDirectory(MemberProfileRepository profiles, OrgQueryService orgQuery, OrgService orgUnits) {
		this.profiles = profiles;
		this.orgQuery = orgQuery;
		this.orgUnits = orgUnits;
	}

	@Transactional(readOnly = true)
	Page<MemberProfile> search(MemberFilter filter, Pageable pageable) {
		List<Specification<MemberProfile>> parts = new ArrayList<>();
		switch (filter.status()) {
			case ACTIVE -> parts.add((r, q, cb) -> cb.isNull(r.get("archivedAt")));
			case ARCHIVED -> parts.add((r, q, cb) -> cb.isNotNull(r.get("archivedAt")));
			case ALL -> {
			}
		}
		if (filter.facultyId() != null) {
			parts.add((r, q, cb) -> cb.equal(r.get("facultyId"), filter.facultyId()));
		}
		if (filter.activityLevelId() != null) {
			parts.add((r, q, cb) -> cb.equal(r.get("activityLevelId"), filter.activityLevelId()));
		}
		if (filter.departmentId() != null) {
			parts.add(userIn(membersOfUnitTree(filter.departmentId())));
		}
		if (filter.divisionId() != null) {
			parts.add(userIn(membersOfUnitTree(filter.divisionId())));
		}
		if (filter.positionId() != null) {
			parts.add(userIn(new HashSet<>(orgQuery.holderIdsOfPosition(filter.positionId()))));
		}
		if (filter.search() != null) {
			for (String token : filter.search().strip().split("\\s+")) {
				if (!token.isEmpty()) {
					parts.add(matchesToken(token));
				}
			}
		}
		Specification<MemberProfile> spec = (r, q, cb) -> cb.conjunction();
		for (Specification<MemberProfile> part : parts) {
			spec = spec.and(part);
		}
		return profiles.findAll(spec, pageable);
	}

	/** Active members of the unit and of all its active descendants (cycle-safe). */
	private Set<UUID> membersOfUnitTree(UUID unitId) {
		Set<UUID> visited = new HashSet<>();
		Set<UUID> members = new LinkedHashSet<>();
		List<UUID> queue = new ArrayList<>(List.of(unitId));
		while (!queue.isEmpty()) {
			UUID current = queue.remove(queue.size() - 1);
			if (!visited.add(current)) {
				continue;
			}
			members.addAll(orgQuery.memberIdsOfUnit(current));
			orgUnits.find(current).map(OrgUnitInfo::childIds).ifPresent(queue::addAll);
		}
		return members;
	}

	private static Specification<MemberProfile> userIn(Collection<UUID> ids) {
		return (r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("userId").in(ids);
	}

	private static Specification<MemberProfile> matchesToken(String rawToken) {
		String token = rawToken.startsWith("@") ? rawToken.substring(1) : rawToken;
		if (token.isEmpty()) {
			return (r, q, cb) -> cb.conjunction();
		}
		String pattern = "%" + escapeLike(token.toLowerCase(Locale.ROOT)) + "%";
		return (r, q, cb) -> {
			Predicate last = cb.like(cb.lower(r.get("lastName")), pattern, LIKE_ESCAPE);
			Predicate first = cb.like(cb.lower(r.get("firstName")), pattern, LIKE_ESCAPE);
			Predicate middle = cb.like(cb.lower(cb.coalesce(r.<String>get("middleName"), "")), pattern, LIKE_ESCAPE);
			Predicate telegram = cb.like(cb.lower(r.get("telegram")), pattern, LIKE_ESCAPE);
			return cb.or(last, first, middle, telegram);
		};
	}

	/** Escapes the LIKE wildcards (and the escape character itself) so user input is matched literally. */
	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
