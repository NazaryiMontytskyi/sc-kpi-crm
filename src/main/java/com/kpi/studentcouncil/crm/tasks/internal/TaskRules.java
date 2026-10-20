package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;

/** Pure rules of a task's own data: labels, attachments, related links and custom field values. */
final class TaskRules {

	static final int MAX_LABELS = 20;
	static final int MAX_LABEL_LENGTH = 50;
	static final int MAX_ATTACHMENTS = 20;
	static final int MAX_RELATED = 50;
	static final int MAX_TEXT_LENGTH = 2000;

	private TaskRules() {
	}

	/** Labels are stripped, lower-cased and de-duplicated; blank or too long labels are rejected. */
	static Set<String> normalizeLabels(Collection<String> labels) {
		Set<String> result = new LinkedHashSet<>();
		if (labels == null) {
			return result;
		}
		for (String raw : labels) {
			String label = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
			if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
				throw new BusinessRuleException("TASK_LABEL_INVALID", MAX_LABEL_LENGTH);
			}
			result.add(label);
		}
		if (result.size() > MAX_LABELS) {
			throw new BusinessRuleException("TASK_TOO_MANY_LABELS", MAX_LABELS);
		}
		return result;
	}

	/** Attachment ids keep their order; duplicates collapse. */
	static List<UUID> normalizeAttachments(Collection<UUID> ids) {
		List<UUID> result = distinct(ids);
		if (result.size() > MAX_ATTACHMENTS) {
			throw new BusinessRuleException("TASK_TOO_MANY_ATTACHMENTS", MAX_ATTACHMENTS);
		}
		return result;
	}

	/** Related ids: distinct, never the task itself. {@code selfId} is null while the task is being created. */
	static Set<UUID> normalizeRelated(Collection<UUID> ids, UUID selfId) {
		Set<UUID> result = new LinkedHashSet<>(distinct(ids));
		if (selfId != null && result.contains(selfId)) {
			throw new BusinessRuleException("TASK_RELATED_SELF");
		}
		if (result.size() > MAX_RELATED) {
			throw new BusinessRuleException("TASK_TOO_MANY_RELATED", MAX_RELATED);
		}
		return result;
	}

	private static List<UUID> distinct(Collection<UUID> ids) {
		List<UUID> result = new ArrayList<>();
		if (ids == null) {
			return result;
		}
		for (UUID id : ids) {
			if (id == null) {
				throw new BusinessRuleException("TASK_REFERENCE_INVALID");
			}
			if (!result.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	/**
	 * Validates the custom field values of a task against the active field definitions of its space and returns the
	 * normalised map (keyed by field id). Unknown field ids, wrong types, options not offered by a select field and
	 * missing required fields are rejected; a null value clears the field.
	 *
	 * @param fields       the space's active definitions
	 * @param values       raw values from the request, keyed by field id (null = none)
	 * @param personExists tells whether a PERSON value refers to an existing user
	 */
	static Map<String, Object> validateCustomFields(List<FieldRule> fields, Map<String, Object> values,
			Predicate<UUID> personExists) {
		Map<String, Object> raw = values == null ? Map.of() : values;
		Map<String, FieldRule> byId = new LinkedHashMap<>();
		fields.forEach(f -> byId.put(f.id().toString(), f));
		for (String key : raw.keySet()) {
			if (!byId.containsKey(normalizeKey(key))) {
				throw new BusinessRuleException("CUSTOM_FIELD_UNKNOWN", key);
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (FieldRule field : fields) {
			Object value = lookup(raw, field.id());
			if (value == null || (value instanceof String s && s.isBlank())) {
				if (field.required()) {
					throw new BusinessRuleException("CUSTOM_FIELD_REQUIRED", field.name());
				}
				continue;
			}
			result.put(field.id().toString(), validateValue(field, value, personExists));
		}
		return result;
	}

	private static Object lookup(Map<String, Object> raw, UUID fieldId) {
		for (Map.Entry<String, Object> e : raw.entrySet()) {
			if (normalizeKey(e.getKey()).equals(fieldId.toString())) {
				return e.getValue();
			}
		}
		return null;
	}

	private static String normalizeKey(String key) {
		return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
	}

	private static Object validateValue(FieldRule field, Object value, Predicate<UUID> personExists) {
		switch (field.type()) {
			case TEXT -> {
				if (value instanceof String s && s.strip().length() <= MAX_TEXT_LENGTH) {
					return s.strip();
				}
			}
			case NUMBER -> {
				if (value instanceof Number n && !(value instanceof Double d && (d.isNaN() || d.isInfinite()))) {
					return n;
				}
			}
			case DATE -> {
				if (value instanceof String s) {
					try {
						return java.time.LocalDate.parse(s.strip()).toString();
					}
					catch (java.time.format.DateTimeParseException e) {
						// falls through to the invalid-value error
					}
				}
			}
			case SELECT -> {
				if (value instanceof String s && field.options().contains(s.strip())) {
					return s.strip();
				}
			}
			case PERSON -> {
				if (value instanceof String s) {
					try {
						UUID id = UUID.fromString(s.strip());
						if (personExists.test(id)) {
							return id.toString();
						}
					}
					catch (IllegalArgumentException e) {
						// falls through to the invalid-value error
					}
				}
			}
		}
		throw new BusinessRuleException("CUSTOM_FIELD_VALUE_INVALID", field.name(), field.type().name());
	}

	/** What the validation needs to know about a custom field definition. */
	record FieldRule(UUID id, String name, com.kpi.studentcouncil.crm.tasks.CustomFieldType type, boolean required,
			List<String> options) {
	}

}
