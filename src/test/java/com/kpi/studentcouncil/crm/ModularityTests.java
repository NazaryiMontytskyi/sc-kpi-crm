package com.kpi.studentcouncil.crm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(CrmApplication.class).verify();
	}

}
