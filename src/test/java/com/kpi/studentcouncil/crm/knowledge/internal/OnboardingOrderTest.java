package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OnboardingOrderTest {

	private static Article article(boolean restricted, boolean archived) {
		Article a = new Article("T", Map.of("type", "doc"), null, restricted, Set.of(), List.of(), Set.of());
		if (archived) {
			a.archive(UUID.randomUUID(), Instant.now());
		}
		return a;
	}

	@Test
	void archivedMissingAndHiddenArticlesAreSkippedAndOrderKept() {
		UUID a = UUID.randomUUID();
		UUID archived = UUID.randomUUID();
		UUID restricted = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		Map<UUID, Article> byId = Map.of(a, article(false, false), archived, article(false, true), restricted,
				article(true, false), c, article(false, false));

		List<UUID> visible = OnboardingService.visibleInOrder(List.of(c, archived, missing, restricted, a), byId,
				x -> !x.isRestricted());

		assertThat(visible).containsExactly(c, a);
	}

}
