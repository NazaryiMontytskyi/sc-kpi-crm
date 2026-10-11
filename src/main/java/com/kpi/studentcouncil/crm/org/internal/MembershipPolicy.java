package com.kpi.studentcouncil.crm.org.internal;

import java.time.LocalDate;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** Pure rules of membership periods. */
final class MembershipPolicy {

	private MembershipPolicy() {
	}

	/** The end date must not be earlier than the start date. */
	static void assertPeriod(LocalDate since, LocalDate until) {
		if (until.isBefore(since)) {
			throw new BusinessRuleException("MEMBERSHIP_INVALID_PERIOD", since, until);
		}
	}

}
