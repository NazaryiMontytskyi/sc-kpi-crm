package com.kpi.studentcouncil.crm.members.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.members.FacultyLookup;
import com.kpi.studentcouncil.crm.members.MemberText;
import com.kpi.studentcouncil.crm.members.MemberTextProvider;

/** Searchable text of member profiles for the search index. */
@Component
class MemberTextProviderImpl implements MemberTextProvider {

	private final MemberProfileRepository profiles;
	private final FacultyLookup faculties;

	MemberTextProviderImpl(MemberProfileRepository profiles, FacultyLookup faculties) {
		this.profiles = profiles;
		this.faculties = faculties;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<MemberText> findMember(UUID memberId) {
		return profiles.findByUserId(memberId).map(this::text);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> findActiveIds() {
		return profiles.findByArchivedAtIsNull().stream().map(MemberProfile::getUserId).toList();
	}

	private MemberText text(MemberProfile p) {
		String fullName = Stream.of(p.getLastName(), p.getFirstName(), p.getMiddleName())
				.filter(s -> s != null && !s.isBlank()).reduce((a, b) -> a + " " + b).orElse("");
		String faculty = faculties.resolve(p.getFacultyId())
				.map(f -> f.code() + " " + f.nameUk() + " " + f.nameEn()).orElse("");
		String details = Stream.of(p.getStudyGroup(), p.getTelegram(), faculty).filter(s -> s != null && !s.isBlank())
				.reduce((a, b) -> a + " " + b).orElse("");
		return new MemberText(p.getUserId(), fullName, details, p.isArchived());
	}

}
