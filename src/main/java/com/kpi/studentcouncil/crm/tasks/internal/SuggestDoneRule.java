package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The "all resolutions accepted" hint (CONTEXT.md 5.3). Pure function, no side effects: the task status is never changed
 * automatically.
 */
final class SuggestDoneRule {

	private SuggestDoneRule() {
	}

	/** A resolution as seen by the rule. */
	record Node(UUID id, UUID parentId, ResolutionState state) {
	}

	/**
	 * True iff the task is not in a DONE-category status, has at least one counted resolution and every counted
	 * resolution is ACCEPTED. CANCELED resolutions are ignored.
	 *
	 * <p>REDIRECTED: the state machine has no transition out of REDIRECTED, so a redirected parent never becomes
	 * ACCEPTED itself. It is therefore represented by its redirect chain: while it has at least one non-canceled child, it
	 * is not counted and the children decide (a child still in progress blocks the flag until the chain ends, an
	 * accepted leaf satisfies it). A REDIRECTED resolution without any non-canceled child (its chain was canceled) is
	 * unfinished work and blocks the flag.
	 */
	static boolean suggest(Collection<Node> resolutions, boolean taskInDoneStatus) {
		if (taskInDoneStatus) {
			return false;
		}
		Set<UUID> parentsWithLiveChild = new HashSet<>();
		for (Node n : resolutions) {
			if (n.parentId() != null && n.state() != ResolutionState.CANCELED) {
				parentsWithLiveChild.add(n.parentId());
			}
		}
		boolean anyCounted = false;
		for (Node n : resolutions) {
			if (n.state() == ResolutionState.CANCELED
					|| (n.state() == ResolutionState.REDIRECTED && parentsWithLiveChild.contains(n.id()))) {
				continue;
			}
			if (n.state() != ResolutionState.ACCEPTED) {
				return false;
			}
			anyCounted = true;
		}
		return anyCounted;
	}

}
