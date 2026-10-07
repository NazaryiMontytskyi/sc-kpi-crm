package com.kpi.studentcouncil.crm.org.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.kpi.studentcouncil.crm.org.OrgUnitType;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

class OrgUnitPolicyTest {

	@ParameterizedTest
	@CsvSource({ "LEADERSHIP,", "DEPARTMENT,", "DEPARTMENT,LEADERSHIP", "DIVISION,DEPARTMENT", "WORKING_GROUP,",
			"WORKING_GROUP,LEADERSHIP", "WORKING_GROUP,DEPARTMENT", "WORKING_GROUP,DIVISION",
			"WORKING_GROUP,WORKING_GROUP" })
	void allowedPlacements(OrgUnitType type, OrgUnitType parent) {
		assertThatCode(() -> OrgUnitPolicy.assertPlacement(type, parent)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource({ "LEADERSHIP,DEPARTMENT", "LEADERSHIP,LEADERSHIP", "DEPARTMENT,DEPARTMENT", "DEPARTMENT,DIVISION",
			"DEPARTMENT,WORKING_GROUP", "DIVISION,DIVISION", "DIVISION,LEADERSHIP", "DIVISION,WORKING_GROUP" })
	void forbiddenPlacements(OrgUnitType type, OrgUnitType parent) {
		assertThatThrownBy(() -> OrgUnitPolicy.assertPlacement(type, parent)).isInstanceOf(BusinessRuleException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("ORG_UNIT_PARENT_NOT_ALLOWED");
	}

	@Test
	void divisionRequiresAParent() {
		assertThatThrownBy(() -> OrgUnitPolicy.assertPlacement(OrgUnitType.DIVISION, null))
				.isInstanceOf(BusinessRuleException.class).extracting(e -> ((DomainException) e).getCode())
				.isEqualTo("ORG_UNIT_PARENT_REQUIRED");
	}

	@Test
	void cycleIsDetectedForSelfAndDescendants() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		Map<UUID, UUID> parents = new HashMap<>();
		parents.put(a, null);
		parents.put(b, a);
		parents.put(c, b);

		assertThatThrownBy(() -> OrgUnitPolicy.assertNoCycle(a, a, parents::get))
				.isInstanceOf(BusinessRuleException.class).extracting(e -> ((DomainException) e).getCode())
				.isEqualTo("ORG_UNIT_CYCLE");
		assertThatThrownBy(() -> OrgUnitPolicy.assertNoCycle(a, c, parents::get))
				.isInstanceOf(BusinessRuleException.class);
		assertThatCode(() -> OrgUnitPolicy.assertNoCycle(c, a, parents::get)).doesNotThrowAnyException();
		assertThatCode(() -> OrgUnitPolicy.assertNoCycle(b, null, parents::get)).doesNotThrowAnyException();
	}

	@Test
	void archivingIsRefusedWhileActiveChildrenExist() {
		assertThatThrownBy(() -> OrgUnitPolicy.assertCanArchive(2)).isInstanceOf(ConflictException.class)
				.extracting(e -> ((DomainException) e).getCode()).isEqualTo("ORG_UNIT_HAS_ACTIVE_CHILDREN");
		assertThatCode(() -> OrgUnitPolicy.assertCanArchive(0)).doesNotThrowAnyException();
	}

}
