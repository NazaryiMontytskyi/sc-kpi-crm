package com.kpi.studentcouncil.crm.tasks;

import java.util.UUID;

/**
 * Published (inside the transaction) when a resolution that was redirected from another one is accepted by its author.
 * The parent resolution is not changed: its executor decides when to submit (CONTEXT.md 5.3). {@code notifications}
 * subscribes to tell the parent executor.
 *
 * @param childId          the accepted resolution
 * @param parentId         the resolution it was redirected from
 * @param taskId           the task both belong to
 * @param parentExecutorId who has to be notified (executor of the parent)
 * @param childExecutorId  who carried out the accepted child
 * @param actorId          the user who accepted it
 */
public record ChildResolutionAccepted(UUID childId, UUID parentId, UUID taskId, UUID parentExecutorId,
		UUID childExecutorId, UUID actorId) {
}
