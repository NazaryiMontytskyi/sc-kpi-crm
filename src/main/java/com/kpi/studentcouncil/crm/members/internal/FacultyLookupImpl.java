package com.kpi.studentcouncil.crm.members.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.members.FacultyInfo;
import com.kpi.studentcouncil.crm.members.FacultyLookup;

@Service
class FacultyLookupImpl implements FacultyLookup {

	private final FacultyRepository faculties;

	FacultyLookupImpl(FacultyRepository faculties) {
		this.faculties = faculties;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isSelectable(UUID id) {
		return id != null && faculties.findByIdAndArchivedAtIsNull(id).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FacultyInfo> resolve(UUID id) {
		return id == null ? Optional.empty() : faculties.findById(id).map(FacultyLookupImpl::toInfo);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FacultyInfo> listActive() {
		return faculties.findByArchivedAtIsNullOrderByNameUkAsc().stream().map(FacultyLookupImpl::toInfo).toList();
	}

	private static FacultyInfo toInfo(Faculty f) {
		return new FacultyInfo(f.getId(), f.getCode(), f.getNameUk(), f.getNameEn(), !f.isArchived());
	}

}
