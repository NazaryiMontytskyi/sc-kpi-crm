package com.kpi.studentcouncil.crm.knowledge.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.DomainException;

class ArticleRulesTest {

	private static Map<String, Object> node(String type, Object... childrenOrText) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("type", type);
		List<Object> children = new ArrayList<>(List.of(childrenOrText));
		if (!children.isEmpty()) {
			map.put("content", children);
		}
		return map;
	}

	private static Map<String, Object> text(String value) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("type", "text");
		map.put("text", value);
		return map;
	}

	private static Map<String, Object> mention(Object id, String entityType, String label) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("id", id);
		if (entityType != null) {
			attrs.put("entityType", entityType);
		}
		if (label != null) {
			attrs.put("label", label);
		}
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("type", "mention");
		map.put("attrs", attrs);
		return map;
	}

	private static String code(Runnable action) {
		try {
			action.run();
		}
		catch (DomainException e) {
			return e.getCode();
		}
		return null;
	}

	// ---- cycle detection ----

	@Test
	void movingUnderItselfIsACycle() {
		UUID a = UUID.randomUUID();
		assertThat(ArticleRules.wouldCreateCycle(a, a, id -> null)).isTrue();
	}

	@Test
	void movingUnderADescendantIsACycle() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		Map<UUID, UUID> parents = Map.of(b, a, c, b);
		assertThat(ArticleRules.wouldCreateCycle(a, c, parents::get)).isTrue();
		assertThat(ArticleRules.wouldCreateCycle(a, b, parents::get)).isTrue();
	}

	@Test
	void movingUnderAnUnrelatedOrAncestorOrNullParentIsFine() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Map<UUID, UUID> parents = Map.of(b, a, c, b);
		assertThat(ArticleRules.wouldCreateCycle(c, a, parents::get)).isFalse();
		assertThat(ArticleRules.wouldCreateCycle(c, other, parents::get)).isFalse();
		assertThat(ArticleRules.wouldCreateCycle(c, null, parents::get)).isFalse();
	}

	@Test
	void anExistingLoopAboveTheNewParentDoesNotHang() {
		UUID x = UUID.randomUUID();
		UUID y = UUID.randomUUID();
		UUID article = UUID.randomUUID();
		Map<UUID, UUID> parents = Map.of(x, y, y, x);
		assertThat(ArticleRules.wouldCreateCycle(article, x, parents::get)).isFalse();
	}

	// ---- references ----

	@Test
	void mentionsAreExtractedWithTheirKindInDocumentOrderAndDeduplicated() {
		UUID person = UUID.randomUUID();
		UUID article = UUID.randomUUID();
		UUID task = UUID.randomUUID();
		Map<String, Object> doc = node("doc",
				node("paragraph", text("See "), mention(article.toString(), "article", "Page"), text(" and "),
						mention(task.toString(), "TASK", null)),
				node("paragraph", mention(person.toString(), null, "Fake Person"),
						mention(person.toString(), "person", null)));
		assertThat(ArticleRules.extractReferences(doc)).containsExactly(
				new ArticleReference(ReferenceType.ARTICLE, article), new ArticleReference(ReferenceType.TASK, task),
				new ArticleReference(ReferenceType.PERSON, person));
	}

	@Test
	void malformedReferencesAreRejected() {
		assertThat(code(() -> ArticleRules.extractReferences(node("doc", mention("not-a-uuid", null, null)))))
				.isEqualTo("ARTICLE_REFERENCE_INVALID");
		assertThat(code(() -> ArticleRules.extractReferences(node("doc", mention(null, null, null)))))
				.isEqualTo("ARTICLE_REFERENCE_INVALID");
		assertThat(code(() -> ArticleRules.extractReferences(
				node("doc", mention(UUID.randomUUID().toString(), "planet", null)))))
				.isEqualTo("ARTICLE_REFERENCE_INVALID");
	}

	@Test
	void tooManyDistinctReferencesAreRejected() {
		List<Object> mentions = new ArrayList<>();
		for (int i = 0; i <= ArticleRules.MAX_REFERENCES; i++) {
			mentions.add(mention(UUID.randomUUID().toString(), null, null));
		}
		assertThat(code(() -> ArticleRules.extractReferences(node("doc", mentions.toArray()))))
				.isEqualTo("ARTICLE_TOO_MANY_REFERENCES");
	}

	// ---- plain text ----

	@Test
	void plainTextHasOneLinePerBlockAndKeepsMentionLabels() {
		Map<String, Object> doc = node("doc", node("heading", text("Title")),
				node("paragraph", text("Hello "), text("world"), mention(UUID.randomUUID().toString(), null, "@Fake")),
				node("bulletList", node("listItem", node("paragraph", text("one"))),
						node("listItem", node("paragraph", text("two")))));
		assertThat(ArticleRules.extractPlainText(doc)).isEqualTo("Title\nHello world@Fake\none\ntwo");
	}

	@Test
	void plainTextOfAnEmptyDocumentIsEmpty() {
		assertThat(ArticleRules.extractPlainText(ArticleRules.normalizeContent(null))).isEmpty();
	}

	// ---- content ----

	@Test
	void missingContentBecomesAnEmptyDocAndOtherRootsAreRejected() {
		assertThat(ArticleRules.normalizeContent(null)).containsEntry("type", "doc");
		assertThat(code(() -> ArticleRules.normalizeContent(node("paragraph")))).isEqualTo("ARTICLE_CONTENT_INVALID");
	}

	@Test
	void tooDeepContentIsRejected() {
		Map<String, Object> deepest = node("doc");
		Map<String, Object> current = deepest;
		for (int i = 0; i < 150; i++) {
			Map<String, Object> child = node("blockquote");
			current.put("content", new ArrayList<>(List.of(child)));
			current = child;
		}
		Map<String, Object> doc = deepest;
		assertThat(code(() -> ArticleRules.normalizeContent(doc))).isEqualTo("ARTICLE_CONTENT_INVALID");
	}

	// ---- tags and attachments ----

	@Test
	void tagsAreStrippedLowerCasedAndDeduplicated() {
		assertThat(ArticleRules.normalizeTags(List.of(" Event ", "event", "FAQ"))).containsExactly("event", "faq");
		assertThat(ArticleRules.normalizeTags(null)).isEmpty();
	}

	@Test
	void invalidTagsAreRejected() {
		assertThatThrownBy(() -> ArticleRules.normalizeTags(List.of("  "))).isInstanceOf(BusinessRuleException.class);
		assertThat(code(() -> ArticleRules.normalizeTags(List.of("x".repeat(51))))).isEqualTo("ARTICLE_TAG_INVALID");
		List<String> many = new ArrayList<>();
		for (int i = 0; i <= ArticleRules.MAX_TAGS; i++) {
			many.add("tag" + i);
		}
		assertThat(code(() -> ArticleRules.normalizeTags(many))).isEqualTo("ARTICLE_TOO_MANY_TAGS");
	}

	@Test
	void attachmentsKeepOrderAndCollapseDuplicates() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertThat(ArticleRules.normalizeAttachments(List.of(b, a, b))).containsExactly(b, a);
		List<UUID> many = new ArrayList<>();
		for (int i = 0; i <= ArticleRules.MAX_ATTACHMENTS; i++) {
			many.add(UUID.randomUUID());
		}
		assertThat(code(() -> ArticleRules.normalizeAttachments(many))).isEqualTo("ARTICLE_TOO_MANY_ATTACHMENTS");
	}

}
