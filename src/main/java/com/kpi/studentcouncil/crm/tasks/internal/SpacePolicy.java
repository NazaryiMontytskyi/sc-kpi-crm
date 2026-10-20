package com.kpi.studentcouncil.crm.tasks.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/** Pure rules of spaces, their statuses and custom fields. */
final class SpacePolicy {

	static final int MAX_OPTIONS = 50;
	static final int MAX_OPTION_LENGTH = 100;

	/** Categories every space must keep at least once among its active statuses. */
	static final List<StatusCategory> REQUIRED_CATEGORIES = List.of(StatusCategory.TODO, StatusCategory.DONE);

	private SpacePolicy() {
	}

	/** A space always has at least one TODO and one DONE status. */
	static void assertRequiredCategories(Collection<StatusCategory> activeCategories) {
		for (StatusCategory required : REQUIRED_CATEGORIES) {
			if (!activeCategories.contains(required)) {
				throw new BusinessRuleException("SPACE_STATUS_CATEGORY_REQUIRED", required.name());
			}
		}
	}

	/** A general space belongs to no unit. */
	static void assertPlacement(boolean general, UUID orgUnitId) {
		if (general && orgUnitId != null) {
			throw new BusinessRuleException("SPACE_GENERAL_HAS_ORG_UNIT");
		}
	}

	/** The new order must list every active status exactly once. */
	static void assertCompleteOrder(Collection<UUID> activeIds, List<UUID> requested) {
		if (requested == null || requested.size() != activeIds.size()
				|| !new HashSet<>(requested).equals(new HashSet<>(activeIds))) {
			throw new BusinessRuleException("SPACE_STATUS_ORDER_MISMATCH");
		}
	}

	/** Names are unique among active siblings, ignoring case. */
	static boolean nameTaken(Collection<String> siblingNames, String name) {
		return siblingNames.stream().anyMatch(n -> n.strip().equalsIgnoreCase(name.strip()));
	}

	/**
	 * Validates and normalises the options: SELECT needs at least one non-blank, distinct (case-insensitive) option;
	 * the other types take none.
	 */
	static List<String> normalizeOptions(CustomFieldType type, List<String> options) {
		if (type != CustomFieldType.SELECT) {
			if (options != null && !options.isEmpty()) {
				throw new BusinessRuleException("CUSTOM_FIELD_OPTIONS_NOT_ALLOWED");
			}
			return List.of();
		}
		if (options == null || options.isEmpty()) {
			throw new BusinessRuleException("CUSTOM_FIELD_OPTIONS_REQUIRED");
		}
		if (options.size() > MAX_OPTIONS) {
			throw new BusinessRuleException("CUSTOM_FIELD_OPTIONS_TOO_MANY", MAX_OPTIONS);
		}
		List<String> cleaned = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String raw : options) {
			String option = raw == null ? "" : raw.strip();
			if (option.isEmpty() || option.length() > MAX_OPTION_LENGTH) {
				throw new BusinessRuleException("CUSTOM_FIELD_OPTION_INVALID", MAX_OPTION_LENGTH);
			}
			if (!seen.add(option.toLowerCase(Locale.ROOT))) {
				throw new BusinessRuleException("CUSTOM_FIELD_OPTION_DUPLICATE", option);
			}
			cleaned.add(option);
		}
		return cleaned;
	}

}
