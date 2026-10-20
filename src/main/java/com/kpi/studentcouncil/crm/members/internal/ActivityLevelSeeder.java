package com.kpi.studentcouncil.crm.members.internal;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.seed.SeedScope;
import com.kpi.studentcouncil.crm.shared.seed.Seeder;

/**
 * Reference seed: PLACEHOLDER activity scale (High/Medium/Low/Inactive) until HR decides the real one (OPEN-Q3).
 * The scale is data: edit, add or archive levels through the API, no code change needed.
 */
@Component
class ActivityLevelSeeder implements Seeder {

	private final ActivityLevelDefinitionRepository levels;
	private final ActivityLevelService service;

	ActivityLevelSeeder(ActivityLevelDefinitionRepository levels, ActivityLevelService service) {
		this.levels = levels;
		this.service = service;
	}

	@Override
	public String id() {
		return "members.reference-activity-levels";
	}

	@Override
	public SeedScope scope() {
		return SeedScope.REFERENCE;
	}

	@Override
	public void run() {
		seed("HIGH", "Високий", "High", 10);
		seed("MEDIUM", "Середній", "Medium", 20);
		seed("LOW", "Низький", "Low", 30);
		seed("INACTIVE", "Неактивний", "Inactive", 40);
	}

	private void seed(String code, String labelUk, String labelEn, int order) {
		if (!levels.existsByCodeIgnoreCase(code)) {
			service.create(code, labelUk, labelEn, order);
		}
	}

}
