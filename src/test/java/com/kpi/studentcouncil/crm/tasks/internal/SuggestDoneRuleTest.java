package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.tasks.internal.SuggestDoneRule.Node;

class SuggestDoneRuleTest {

	private static Node node(ResolutionState state) {
		return new Node(UUID.randomUUID(), null, state);
	}

	private static Node child(Node parent, ResolutionState state) {
		return new Node(UUID.randomUUID(), parent.id(), state);
	}

	@Test
	void noResolutionsMeansNoSuggestion() {
		assertThat(SuggestDoneRule.suggest(List.of(), false)).isFalse();
	}

	@Test
	void allAcceptedSuggests() {
		assertThat(SuggestDoneRule.suggest(List.of(node(ResolutionState.ACCEPTED), node(ResolutionState.ACCEPTED)), false))
				.isTrue();
	}

	@Test
	void anyNonFinalStateBlocks() {
		for (ResolutionState s : List.of(ResolutionState.NEW, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED,
				ResolutionState.REWORK, ResolutionState.REDIRECTED)) {
			assertThat(SuggestDoneRule.suggest(List.of(node(ResolutionState.ACCEPTED), node(s)), false)).as(s.name())
					.isFalse();
		}
	}

	@Test
	void canceledAreIgnoredButOnlyCanceledIsNotEnough() {
		assertThat(SuggestDoneRule.suggest(List.of(node(ResolutionState.ACCEPTED), node(ResolutionState.CANCELED)), false))
				.isTrue();
		assertThat(SuggestDoneRule.suggest(List.of(node(ResolutionState.CANCELED)), false)).isFalse();
	}

	@Test
	void doneCategoryStatusNeverSuggests() {
		assertThat(SuggestDoneRule.suggest(List.of(node(ResolutionState.ACCEPTED)), true)).isFalse();
	}

	@Test
	void redirectedParentIsRepresentedByItsChain() {
		Node parent = node(ResolutionState.REDIRECTED);
		assertThat(SuggestDoneRule.suggest(List.of(parent, child(parent, ResolutionState.IN_PROGRESS)), false)).isFalse();
		assertThat(SuggestDoneRule.suggest(List.of(parent, child(parent, ResolutionState.ACCEPTED)), false)).isTrue();
	}

	@Test
	void deepChainNeedsItsLeafAccepted() {
		Node a = node(ResolutionState.REDIRECTED);
		Node b = child(a, ResolutionState.REDIRECTED);
		Node c = child(b, ResolutionState.SUBMITTED);
		assertThat(SuggestDoneRule.suggest(List.of(a, b, c), false)).isFalse();
		Node accepted = child(b, ResolutionState.ACCEPTED);
		assertThat(SuggestDoneRule.suggest(List.of(a, b, accepted), false)).isTrue();
	}

	@Test
	void redirectedWithOnlyCanceledChildrenBlocks() {
		Node parent = node(ResolutionState.REDIRECTED);
		assertThat(SuggestDoneRule.suggest(List.of(parent, child(parent, ResolutionState.CANCELED),
				node(ResolutionState.ACCEPTED)), false)).isFalse();
	}

}
