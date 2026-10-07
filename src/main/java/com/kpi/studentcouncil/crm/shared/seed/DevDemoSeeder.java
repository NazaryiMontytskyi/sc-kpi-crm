package com.kpi.studentcouncil.crm.shared.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder dev-only seeder proving the mechanism end to end. Fake users and other sample data are added by the
 * modules that own them (users: INC-015) as further {@link Seeder} beans with {@link SeedScope#DEV_ONLY}.
 */
@Component
class DevDemoSeeder implements Seeder {

	private static final Logger log = LoggerFactory.getLogger(DevDemoSeeder.class);

	@Override
	public String id() {
		return "shared.dev-demo";
	}

	@Override
	public SeedScope scope() {
		return SeedScope.DEV_ONLY;
	}

	@Override
	public void run() {
		log.info("Dev seed data loaded (no sample rows yet; fake users arrive with INC-015)");
	}

}
