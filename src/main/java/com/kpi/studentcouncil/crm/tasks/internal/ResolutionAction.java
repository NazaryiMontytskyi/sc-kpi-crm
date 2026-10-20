package com.kpi.studentcouncil.crm.tasks.internal;

/** What an actor does to a resolution. Each action maps to transitions in {@link ResolutionStateMachine}. */
enum ResolutionAction {
	/** Executor takes the resolution into work: NEW to IN_PROGRESS. */
	START,
	/** Executor submits with a mandatory completion note: IN_PROGRESS to SUBMITTED. */
	SUBMIT,
	/** Author accepts the result: SUBMITTED to ACCEPTED. */
	ACCEPT,
	/** Author returns it with a mandatory comment: SUBMITTED to REWORK. */
	REQUEST_REWORK,
	/** Executor resumes after rework: REWORK to IN_PROGRESS. */
	RESUME,
	/** Executor hands it on (child created by INC-034): IN_PROGRESS to REDIRECTED. */
	REDIRECT,
	/** Author cancels: any non-final state to CANCELED. */
	CANCEL
}
