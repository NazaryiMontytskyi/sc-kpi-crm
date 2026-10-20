package com.kpi.studentcouncil.crm.tasks.internal;

/** Lifecycle state of a resolution (CONTEXT.md 5.3), independent of task statuses. */
enum ResolutionState {
	NEW, IN_PROGRESS, SUBMITTED, REWORK, ACCEPTED, REDIRECTED, CANCELED;

	/** ACCEPTED and CANCELED are final: no transition leaves them. */
	boolean isFinal() {
		return this == ACCEPTED || this == CANCELED;
	}
}
