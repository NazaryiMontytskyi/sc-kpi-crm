package com.kpi.studentcouncil.crm.org.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

@Service
class OrgServiceImpl implements OrgService {

	private final OrgUnitRepository units;
	private final OrgUnitService service;

	OrgServiceImpl(OrgUnitRepository units, OrgUnitService service) {
		this.units = units;
		this.service = service;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OrgUnitInfo> find(UUID id) {
		return units.findById(id).map(service::info);
	}

	@Override
	@Transactional(readOnly = true)
	public OrgUnitInfo require(UUID id) {
		return find(id).orElseThrow(() -> new NotFoundException(OrgUnitService.ENTITY_TYPE, id));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isActive(UUID id) {
		return units.findByIdAndArchivedAtIsNull(id).isPresent();
	}

}
