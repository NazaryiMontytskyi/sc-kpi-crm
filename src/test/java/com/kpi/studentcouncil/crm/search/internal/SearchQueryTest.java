package com.kpi.studentcouncil.crm.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

	@Test
	void blankAndTooShortQueriesAreRejected() {
		assertThat(SearchQuery.parse(null)).isEmpty();
		assertThat(SearchQuery.parse("   ")).isEmpty();
		assertThat(SearchQuery.parse("a")).isEmpty();
		assertThat(SearchQuery.parse("!!")).isEmpty();
	}

	@Test
	void queryIsLowerCasedAndSplitIntoWords() {
		SearchQuery q = SearchQuery.parse("  Резол   Бюджет-2026 ").orElseThrow();

		assertThat(q.text()).isEqualTo("резол бюджет-2026");
		assertThat(q.tokens()).isEqualTo(List.of("резол", "бюджет", "2026"));
	}

	@Test
	void everyWordBecomesAPrefixTerm() {
		SearchQuery q = SearchQuery.parse("Резол budg").orElseThrow();

		assertThat(q.prefixTsQuery()).isEqualTo("резол:* & budg:*");
	}

	@Test
	void tsQuerySyntaxCharactersNeverReachTheTsQuery() {
		SearchQuery q = SearchQuery.parse("a' | !b & (c):*").orElseThrow();

		assertThat(q.prefixTsQuery()).isEqualTo("a:* & b:* & c:*");
	}

	@Test
	void likePatternEscapesWildcards() {
		SearchQuery q = SearchQuery.parse("50%_x\\y").orElseThrow();

		assertThat(q.likePattern()).isEqualTo("%50\\%\\_x\\\\y%");
	}

	@Test
	void longQueriesAreShortened() {
		SearchQuery q = SearchQuery.parse("x".repeat(500)).orElseThrow();

		assertThat(q.text()).hasSize(SearchQuery.MAX_LENGTH);
	}

	@Test
	void snippetCentersOnTheFirstMatch() {
		String body = "word ".repeat(60) + "Бюджет затверджено " + "tail ".repeat(60);
		SearchQuery q = SearchQuery.parse("бюджет").orElseThrow();

		String snippet = q.snippet(body);

		assertThat(snippet).contains("Бюджет затверджено").startsWith("…").endsWith("…");
		assertThat(snippet.length()).isLessThan(220);
	}

	@Test
	void snippetOfShortBodyIsTheBodyAndBlankBodyGivesEmpty() {
		SearchQuery q = SearchQuery.parse("abc").orElseThrow();

		assertThat(q.snippet("Short\n text")).isEqualTo("Short text");
		assertThat(q.snippet("  ")).isEmpty();
		assertThat(q.snippet(null)).isEmpty();
	}

}
