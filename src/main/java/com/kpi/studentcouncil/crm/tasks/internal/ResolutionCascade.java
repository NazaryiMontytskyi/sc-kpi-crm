package com.kpi.studentcouncil.crm.tasks.internal;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Canceling a resolution cancels its non-final descendants (the REDIRECTED chain) recursively (decision Q6). Plan first,
 * mutate afterwards: when the root cancel is not allowed nothing is changed, so the operation is atomic in memory; the
 * service adds the database transaction.
 */
final class ResolutionCascade {

	private ResolutionCascade() {
	}

	/**
	 * Cancels {@code root} as {@code actorId} and every non-final descendant; ACCEPTED and CANCELED descendants are left
	 * untouched. Each canceled descendant gets a history entry referencing the root.
	 *
	 * @param childrenOf direct children of a resolution id
	 * @return all resolutions whose state changed: the root first, then descendants in breadth-first order
	 */
	static List<Resolution> cancel(Resolution root, UUID actorId, String comment, Instant at,
			Function<UUID, List<Resolution>> childrenOf) {
		// 1. validate the root cancel (transition + author) without changing anything
		ResolutionStateMachine.check(root.getState(), ResolutionAction.CANCEL, actorId, root.getAuthorId(),
				root.getExecutorId(), comment);
		// 2. plan: collect non-final descendants
		List<Resolution> affected = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		seen.add(root.getId());
		Deque<Resolution> queue = new ArrayDeque<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			for (Resolution child : childrenOf.apply(queue.poll().getId())) {
				if (!seen.add(child.getId())) {
					continue;
				}
				queue.add(child);
				if (!child.getState().isFinal()) {
					affected.add(child);
				}
			}
		}
		// 3. apply: cannot fail any more (all affected are non-final)
		String text = comment == null || comment.isBlank() ? null : comment.strip();
		root.apply(ResolutionAction.CANCEL, actorId, comment, at);
		affected.forEach(r -> r.cancelByCascade(actorId, root.getId(), text, at));
		List<Resolution> result = new ArrayList<>();
		result.add(root);
		result.addAll(affected);
		return result;
	}

}
