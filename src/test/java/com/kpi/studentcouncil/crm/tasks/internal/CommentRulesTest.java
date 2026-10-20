package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.DomainException;

class CommentRulesTest {

	private static Map<String, Object> text(String value) {
		return Map.of("type", "text", "text", value);
	}

	private static Map<String, Object> mention(Object id) {
		return Map.of("type", "mention", "attrs", Map.of("id", id.toString(), "label", "Fake Person"));
	}

	private static Map<String, Object> doc(Object... inline) {
		return Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(inline))));
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

	@Test
	void aBodyWithTextOrAMentionIsValid() {
		assertThatCode(() -> CommentRules.validateBody(doc(text("hello")), false)).doesNotThrowAnyException();
		assertThatCode(() -> CommentRules.validateBody(doc(mention(UUID.randomUUID())), false))
				.doesNotThrowAnyException();
	}

	@Test
	void anEmptyBodyIsRejectedUnlessThereAreAttachments() {
		assertThat(code(() -> CommentRules.validateBody(doc(text("   ")), false))).isEqualTo("COMMENT_EMPTY");
		assertThat(code(() -> CommentRules.validateBody(Map.of("type", "doc"), false))).isEqualTo("COMMENT_EMPTY");
		assertThatCode(() -> CommentRules.validateBody(Map.of("type", "doc"), true)).doesNotThrowAnyException();
	}

	@Test
	void aBodyThatIsNotADocumentIsRejected() {
		assertThat(code(() -> CommentRules.validateBody(null, false))).isEqualTo("COMMENT_BODY_INVALID");
		assertThat(code(() -> CommentRules.validateBody(Map.of("type", "paragraph"), false)))
				.isEqualTo("COMMENT_BODY_INVALID");
	}

	@Test
	void aTooLongTextIsRejected() {
		assertThat(code(() -> CommentRules.validateBody(doc(text("x".repeat(CommentRules.MAX_TEXT_LENGTH + 1))), false)))
				.isEqualTo("COMMENT_TOO_LONG");
	}

	@Test
	void mentionsAreExtractedInDocumentOrderWithoutDuplicates() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertThat(CommentRules.extractMentions(doc(mention(a), text(" and "), mention(b), mention(a))))
				.containsExactly(a, b);
	}

	@Test
	void aMentionWithoutAValidIdIsRejected() {
		assertThat(code(() -> CommentRules.extractMentions(doc(mention("not-a-uuid")))))
				.isEqualTo("COMMENT_MENTION_INVALID");
		assertThat(code(() -> CommentRules.extractMentions(doc(Map.of("type", "mention")))))
				.isEqualTo("COMMENT_MENTION_INVALID");
	}

	@Test
	void sentMentionsMustMatchTheBodyExactly() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Map<String, Object> body = doc(mention(a));
		assertThat(CommentRules.checkMentions(List.of(a), body)).containsExactly(a);
		assertThat(code(() -> CommentRules.checkMentions(List.of(a, b), body))).isEqualTo("COMMENT_MENTIONS_MISMATCH");
		assertThat(code(() -> CommentRules.checkMentions(List.of(), body))).isEqualTo("COMMENT_MENTIONS_MISMATCH");
		assertThat(code(() -> CommentRules.checkMentions(null, body))).isEqualTo("COMMENT_MENTIONS_MISMATCH");
		assertThat(CommentRules.checkMentions(null, doc(text("plain")))).isEmpty();
	}

	@Test
	void tooManyMentionsAreRejected() {
		List<Object> inline = new ArrayList<>();
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i <= CommentRules.MAX_MENTIONS; i++) {
			UUID id = UUID.randomUUID();
			ids.add(id);
			inline.add(mention(id));
		}
		assertThat(code(() -> CommentRules.checkMentions(ids, doc(inline.toArray()))))
				.isEqualTo("COMMENT_TOO_MANY_MENTIONS");
	}

	@Test
	void attachmentsKeepOrderCollapseDuplicatesAndAreLimited() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertThat(CommentRules.normalizeAttachments(List.of(b, a, b))).containsExactly(b, a);
		assertThat(CommentRules.normalizeAttachments(null)).isEmpty();
		List<UUID> many = new ArrayList<>();
		for (int i = 0; i <= CommentRules.MAX_ATTACHMENTS; i++) {
			many.add(UUID.randomUUID());
		}
		assertThatThrownBy(() -> CommentRules.normalizeAttachments(many)).isInstanceOf(DomainException.class)
				.hasMessage("COMMENT_TOO_MANY_ATTACHMENTS");
	}

}
