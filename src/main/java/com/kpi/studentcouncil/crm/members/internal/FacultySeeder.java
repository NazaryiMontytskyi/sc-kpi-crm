package com.kpi.studentcouncil.crm.members.internal;

import org.springframework.stereotype.Component;

import com.kpi.studentcouncil.crm.shared.seed.SeedScope;
import com.kpi.studentcouncil.crm.shared.seed.Seeder;

/**
 * Reference seed: generic placeholder faculties so pickers are not empty. The real list is supplied by humans and
 * maintained by {@code settings.manage} holders; the placeholders can be renamed or archived there.
 */
@Component
class FacultySeeder implements Seeder {

	static final int COUNT = 10;

	private final FacultyRepository faculties;
	private final FacultyService service;

	FacultySeeder(FacultyRepository faculties, FacultyService service) {
		this.faculties = faculties;
		this.service = service;
	}

	@Override
	public String id() {
		return "members.reference-faculties";
	}

	@Override
	public SeedScope scope() {
		return SeedScope.REFERENCE;
	}

	@Override
	public void run() {
		for (int i = 1; i <= COUNT; i++) {
			String number = String.format("%02d", i);
			String code = "FAC-" + number;
			if (!faculties.existsByCodeIgnoreCase(code)) {
				service.create(code, "Факультет " + number, "Faculty " + number);
			}
		}
	}

}
