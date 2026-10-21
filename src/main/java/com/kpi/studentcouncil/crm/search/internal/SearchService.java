package com.kpi.studentcouncil.crm.search.internal;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.Permission;
import com.kpi.studentcouncil.crm.search.internal.SearchDtos.SearchGroup;
import com.kpi.studentcouncil.crm.search.internal.SearchDtos.SearchHit;
import com.kpi.studentcouncil.crm.search.internal.SearchDtos.SearchResponse;

/**
 * Answers a query. A group is searched only when the caller holds the view permission of its type (otherwise it is
 * empty); restricted articles additionally need {@code kb.restricted.view}.
 */
@Service
class SearchService {

	static final int DEFAULT_LIMIT = 5;
	static final int MAX_LIMIT = 20;

	private final SearchIndex index;

	SearchService(SearchIndex index) {
		this.index = index;
	}

	@Transactional(readOnly = true)
	SearchResponse search(String rawQuery, Set<SearchType> types, Integer limit) {
		int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
		Set<String> permissions = currentPermissions();
		Optional<SearchQuery> parsed = SearchQuery.parse(rawQuery);
		boolean canRestricted = permissions.contains(Permission.KB_RESTRICTED_VIEW.key());
		Map<SearchType, SearchGroup> groups = new EnumMap<>(SearchType.class);
		for (SearchType type : SearchType.values()) {
			boolean wanted = types == null || types.isEmpty() || types.contains(type);
			if (parsed.isEmpty() || !wanted || !permissions.contains(type.viewPermissionKey())) {
				groups.put(type, new SearchGroup(0, List.of()));
				continue;
			}
			SearchQuery query = parsed.get();
			List<SearchIndex.Hit> hits = index.search(type, query, canRestricted, size);
			groups.put(type, new SearchGroup(hits.isEmpty() ? 0 : hits.get(0).total(),
					hits.stream().map(h -> new SearchHit(h.id(), h.title(), query.snippet(h.body()), h.parentId(),
							h.restricted())).toList()));
		}
		return new SearchResponse(parsed.map(SearchQuery::text).orElse(rawQuery == null ? "" : rawQuery.strip()),
				groups.get(SearchType.TASK), groups.get(SearchType.RESOLUTION), groups.get(SearchType.ARTICLE),
				groups.get(SearchType.PERSON), groups.get(SearchType.ORG_UNIT));
	}

	private static Set<String> currentPermissions() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			return Set.of();
		}
		return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
	}

}
