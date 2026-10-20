package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.internal.TaskRules.FieldRule;

class TaskRulesTest {

	private static final UUID KNOWN_USER = UUID.randomUUID();

	private final FieldRule text = rule("Note", CustomFieldType.TEXT, false, List.of());
	private final FieldRule number = rule("Hours", CustomFieldType.NUMBER, false, List.of());
	private final FieldRule date = rule("Review date", CustomFieldType.DATE, false, List.of());
	private final FieldRule select = rule("Size", CustomFieldType.SELECT, false, List.of("S", "M", "L"));
	private final FieldRule person = rule("Reviewer", CustomFieldType.PERSON, false, List.of());

	private static FieldRule rule(String name, CustomFieldType type, boolean required, List<String> options) {
		return new FieldRule(UUID.randomUUID(), name, type, required, options);
	}

	private Map<String, Object> validate(List<FieldRule> fields, Map<String, Object> values) {
		return TaskRules.validateCustomFields(fields, values, KNOWN_USER::equals);
	}

	private static String code(Runnable action) {
		return ((DomainException) catchThrowable(action)).getCode();
	}

	private static Throwable catchThrowable(Runnable action) {
		try {
			action.run();
		}
		catch (RuntimeException e) {
			return e;
		}
		throw new AssertionError("expected an exception");
	}

	@Test
	void validValuesOfEveryTypeAreAcceptedAndNormalised() {
		Map<String, Object> raw = new HashMap<>();
		raw.put(text.id().toString(), "  hello ");
		raw.put(number.id().toString(), 12.5);
		raw.put(date.id().toString(), "2026-11-03");
		raw.put(select.id().toString(), "M");
		raw.put(person.id().toString(), KNOWN_USER.toString());

		Map<String, Object> result = validate(List.of(text, number, date, select, person), raw);

		assertThat(result).containsEntry(text.id().toString(), "hello").containsEntry(number.id().toString(), 12.5)
				.containsEntry(date.id().toString(), "2026-11-03").containsEntry(select.id().toString(), "M")
				.containsEntry(person.id().toString(), KNOWN_USER.toString());
		assertThat(validate(List.of(number), Map.of(number.id().toString(), new BigDecimal("3")))).hasSize(1);
	}

	@Test
	void wrongTypesAreRejected() {
		assertThat(code(() -> validate(List.of(number), Map.of(number.id().toString(), "12"))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(number), Map.of(number.id().toString(), true))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(text), Map.of(text.id().toString(), 5))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(date), Map.of(date.id().toString(), "03.11.2026"))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(select), Map.of(select.id().toString(), "XL"))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(person), Map.of(person.id().toString(), "not-a-uuid"))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(person), Map.of(person.id().toString(), UUID.randomUUID().toString()))))
				.isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
		assertThat(code(() -> validate(List.of(text),
				Map.of(text.id().toString(), "x".repeat(TaskRules.MAX_TEXT_LENGTH + 1))))).isEqualTo("CUSTOM_FIELD_VALUE_INVALID");
	}

	@Test
	void unknownFieldIdsAreRejected() {
		assertThat(code(() -> validate(List.of(text), Map.of(UUID.randomUUID().toString(), "x"))))
				.isEqualTo("CUSTOM_FIELD_UNKNOWN");
		assertThat(code(() -> validate(List.of(), Map.of("not-an-id", "x")))).isEqualTo("CUSTOM_FIELD_UNKNOWN");
	}

	@Test
	void requiredFieldsMustBePresentAndNullClearsOptionalOnes() {
		FieldRule required = rule("Must", CustomFieldType.TEXT, true, List.of());
		assertThat(code(() -> validate(List.of(required), null))).isEqualTo("CUSTOM_FIELD_REQUIRED");
		Map<String, Object> blank = new HashMap<>();
		blank.put(required.id().toString(), "  ");
		assertThat(code(() -> validate(List.of(required), blank))).isEqualTo("CUSTOM_FIELD_REQUIRED");

		Map<String, Object> cleared = new HashMap<>();
		cleared.put(text.id().toString(), null);
		assertThat(validate(List.of(text), cleared)).isEmpty();
		assertThat(validate(List.of(text), null)).isEmpty();
	}

	@Test
	void fieldIdsAreMatchedCaseInsensitively() {
		Map<String, Object> raw = Map.of(text.id().toString().toUpperCase(), "v");
		assertThat(validate(List.of(text), raw)).containsEntry(text.id().toString(), "v");
	}

	@Test
	void labelsAreLowerCasedDeduplicatedAndBounded() {
		assertThat(TaskRules.normalizeLabels(List.of(" Urgent ", "urgent", "Фінанси"))).containsExactly("urgent", "фінанси");
		assertThat(TaskRules.normalizeLabels(null)).isEmpty();
		assertThat(code(() -> TaskRules.normalizeLabels(List.of("  ")))).isEqualTo("TASK_LABEL_INVALID");
		assertThat(code(() -> TaskRules.normalizeLabels(List.of("x".repeat(TaskRules.MAX_LABEL_LENGTH + 1)))))
				.isEqualTo("TASK_LABEL_INVALID");
		List<String> many = new java.util.ArrayList<>();
		for (int i = 0; i <= TaskRules.MAX_LABELS; i++) {
			many.add("label" + i);
		}
		assertThat(code(() -> TaskRules.normalizeLabels(many))).isEqualTo("TASK_TOO_MANY_LABELS");
	}

	@Test
	void relatedTasksAreDistinctAndNeverTheTaskItself() {
		UUID self = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		assertThat(TaskRules.normalizeRelated(List.of(other, other), self)).containsExactly(other);
		assertThat(code(() -> TaskRules.normalizeRelated(List.of(self), self))).isEqualTo("TASK_RELATED_SELF");
		assertThat(TaskRules.normalizeRelated(List.of(self), null)).containsExactly(self);
		assertThatThrownBy(() -> TaskRules.normalizeRelated(java.util.Arrays.asList(other, null), self))
				.isInstanceOf(DomainException.class);
	}

	@Test
	void attachmentsKeepOrderAndAreBounded() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertThat(TaskRules.normalizeAttachments(List.of(b, a, b))).containsExactly(b, a);
		List<UUID> many = new java.util.ArrayList<>();
		for (int i = 0; i <= TaskRules.MAX_ATTACHMENTS; i++) {
			many.add(UUID.randomUUID());
		}
		assertThat(code(() -> TaskRules.normalizeAttachments(many))).isEqualTo("TASK_TOO_MANY_ATTACHMENTS");
		assertThat(Set.copyOf(TaskRules.normalizeAttachments(null))).isEmpty();
	}

	@Test
	void linkKeyIsCanonicalAndMatchesPostgresUuidOrder() {
		// the high bit set makes UUID.compareTo order these differently from the unsigned (PostgreSQL) order
		UUID negative = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
		UUID positive = UUID.fromString("00000000-0000-0000-0000-000000000002");
		assertThat(negative.compareTo(positive)).isLessThan(0);
		TaskLinkId id = TaskLinkId.of(negative, positive);
		assertThat(id.low()).isEqualTo(positive);
		assertThat(id.high()).isEqualTo(negative);
		assertThat(TaskLinkId.of(positive, negative)).isEqualTo(id);
		assertThat(id.other(positive)).isEqualTo(negative);
		assertThat(id.other(negative)).isEqualTo(positive);
	}

}
