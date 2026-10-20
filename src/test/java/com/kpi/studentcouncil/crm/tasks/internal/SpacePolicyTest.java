package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.kpi.studentcouncil.crm.shared.error.DomainException;
import com.kpi.studentcouncil.crm.tasks.CustomFieldType;
import com.kpi.studentcouncil.crm.tasks.StatusCategory;

class SpacePolicyTest {

	private static String code(Throwable t) {
		return ((DomainException) t).getCode();
	}

	@Test
	void spaceNeedsAtLeastOneTodoAndOneDoneStatus() {
		assertThatCode(() -> SpacePolicy.assertRequiredCategories(List.of(StatusCategory.TODO, StatusCategory.DONE)))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> SpacePolicy.assertRequiredCategories(
				List.of(StatusCategory.IN_PROGRESS, StatusCategory.DONE, StatusCategory.CANCELED)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_CATEGORY_REQUIRED"));
		assertThatThrownBy(() -> SpacePolicy.assertRequiredCategories(List.of(StatusCategory.TODO)))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_CATEGORY_REQUIRED"));
		assertThatThrownBy(() -> SpacePolicy.assertRequiredCategories(List.of()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_CATEGORY_REQUIRED"));
	}

	@Test
	void generalSpaceCannotBeBoundToUnit() {
		assertThatCode(() -> SpacePolicy.assertPlacement(true, null)).doesNotThrowAnyException();
		assertThatCode(() -> SpacePolicy.assertPlacement(false, UUID.randomUUID())).doesNotThrowAnyException();
		assertThatThrownBy(() -> SpacePolicy.assertPlacement(true, UUID.randomUUID()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_GENERAL_HAS_ORG_UNIT"));
	}

	@Test
	void orderMustListEveryActiveStatusExactlyOnce() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		assertThatCode(() -> SpacePolicy.assertCompleteOrder(List.of(a, b, c), List.of(c, a, b)))
				.doesNotThrowAnyException();
		for (List<UUID> bad : List.of(List.of(a, b), List.of(a, b, b), List.of(a, b, UUID.randomUUID()),
				List.of(a, b, c, c))) {
			assertThatThrownBy(() -> SpacePolicy.assertCompleteOrder(List.of(a, b, c), bad))
					.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_ORDER_MISMATCH"));
		}
		assertThatThrownBy(() -> SpacePolicy.assertCompleteOrder(List.of(a), null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SPACE_STATUS_ORDER_MISMATCH"));
	}

	@Test
	void nameComparisonIgnoresCaseAndSurroundingSpaces() {
		assertThat(SpacePolicy.nameTaken(List.of("Review"), " review ")).isTrue();
		assertThat(SpacePolicy.nameTaken(List.of("Review"), "Reviewed")).isFalse();
	}

	@Test
	void selectFieldRequiresDistinctNonBlankOptions() {
		assertThat(SpacePolicy.normalizeOptions(CustomFieldType.SELECT, List.of(" Low ", "High")))
				.containsExactly("Low", "High");
		assertThatThrownBy(() -> SpacePolicy.normalizeOptions(CustomFieldType.SELECT, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTIONS_REQUIRED"));
		assertThatThrownBy(() -> SpacePolicy.normalizeOptions(CustomFieldType.SELECT, List.of()))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTIONS_REQUIRED"));
		assertThatThrownBy(() -> SpacePolicy.normalizeOptions(CustomFieldType.SELECT, List.of("A", "  ")))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTION_INVALID"));
		assertThatThrownBy(() -> SpacePolicy.normalizeOptions(CustomFieldType.SELECT, List.of("A", "a")))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTION_DUPLICATE"));
		assertThatThrownBy(() -> SpacePolicy.normalizeOptions(CustomFieldType.SELECT, List.of("x".repeat(101))))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTION_INVALID"));
	}

	@Test
	void otherFieldTypesTakeNoOptions() {
		for (CustomFieldType type : List.of(CustomFieldType.TEXT, CustomFieldType.NUMBER, CustomFieldType.DATE,
				CustomFieldType.PERSON)) {
			assertThat(SpacePolicy.normalizeOptions(type, null)).isEmpty();
			assertThat(SpacePolicy.normalizeOptions(type, List.of())).isEmpty();
			assertThatThrownBy(() -> SpacePolicy.normalizeOptions(type, List.of("A")))
					.satisfies(e -> assertThat(code(e)).isEqualTo("CUSTOM_FIELD_OPTIONS_NOT_ALLOWED"));
		}
	}

}
