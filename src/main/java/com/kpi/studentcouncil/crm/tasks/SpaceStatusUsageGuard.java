package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Extension point: the part of the tasks module that owns tasks (INC-025) implements this so a status in use cannot
 * be archived without mapping its tasks to another status. Until tasks exist no bean is registered and no status is
 * ever "in use". All registered beans are consulted.
 */
public interface SpaceStatusUsageGuard {

	/** Number of (non-archived) tasks of the space that currently have the status. */
	long countUsages(UUID spaceId, UUID statusId);

	/**
	 * Moves every task of the space from one status to another. Called in the transaction that archives the status,
	 * after the replacement was validated (same space, active).
	 */
	void reassign(UUID spaceId, UUID fromStatusId, UUID toStatusId);

}
