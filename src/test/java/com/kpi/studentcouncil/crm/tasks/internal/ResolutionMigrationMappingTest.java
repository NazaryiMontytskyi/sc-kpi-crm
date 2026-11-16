package com.kpi.studentcouncil.crm.tasks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.kpi.studentcouncil.crm.tasks.StatusCategory;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.ParentTask;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceHistory;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceReminder;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SourceResolution;
import com.kpi.studentcouncil.crm.tasks.internal.ResolutionMigrationMapping.SpaceStatusOption;

class ResolutionMigrationMappingTest {

	private static final ResolutionMigrationMapping UK = ResolutionMigrationMapping.stored();

	private static final ResolutionMigrationMapping EN = ResolutionMigrationMapping.forLocale(Locale.ENGLISH);

	private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");

	private static UUID uuid(String name) {
		return UUID.nameUUIDFromBytes(("fake:" + name).getBytes());
	}

	// ---- state mapping ----

	@ParameterizedTest
	@CsvSource({ "NEW, TODO", "IN_PROGRESS, IN_PROGRESS", "REWORK, IN_PROGRESS", "SUBMITTED, IN_PROGRESS",
			"ACCEPTED, DONE", "CANCELED, CANCELED", "REDIRECTED, CANCELED" })
	void mapsEveryLegacyStateToItsCategory(ResolutionState state, StatusCategory category) {
		assertThat(ResolutionMigrationMapping.targetCategory(state)).isEqualTo(category);
	}

	@Test
	void picksTheFirstActiveStatusOfTheCategoryByPositionThenId() {
		UUID archivedFirst = uuid("archived");
		UUID second = uuid("second");
		UUID third = uuid("third");
		List<SpaceStatusOption> statuses = List.of(
				new SpaceStatusOption(uuid("todo"), StatusCategory.TODO, 0, false),
				new SpaceStatusOption(archivedFirst, StatusCategory.IN_PROGRESS, 1, true),
				new SpaceStatusOption(third, StatusCategory.IN_PROGRESS, 3, false),
				new SpaceStatusOption(second, StatusCategory.IN_PROGRESS, 2, false));
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.IN_PROGRESS, statuses)).contains(second);

		// equal positions: the lower id in PostgreSQL order (= string order)
		UUID a = UUID.fromString("80000000-0000-0000-0000-000000000001");
		UUID b = UUID.fromString("10000000-0000-0000-0000-000000000001");
		List<SpaceStatusOption> tie = List.of(new SpaceStatusOption(a, StatusCategory.DONE, 5, false),
				new SpaceStatusOption(b, StatusCategory.DONE, 5, false));
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.DONE, tie)).contains(b);
	}

	@Test
	void fallsBackAsDocumentedInAdr0010() {
		UUID todo = uuid("todo");
		UUID done = uuid("done");
		UUID laterTodo = uuid("later-todo");
		List<SpaceStatusOption> noProgressNoCanceled = List.of(
				new SpaceStatusOption(done, StatusCategory.DONE, 0, false),
				new SpaceStatusOption(laterTodo, StatusCategory.TODO, 4, false),
				new SpaceStatusOption(todo, StatusCategory.TODO, 1, false),
				new SpaceStatusOption(uuid("archived-canceled"), StatusCategory.CANCELED, 2, true));
		assertThat(ResolutionMigrationMapping.categoryOrder(StatusCategory.IN_PROGRESS))
				.containsExactly(StatusCategory.IN_PROGRESS, StatusCategory.TODO);
		assertThat(ResolutionMigrationMapping.categoryOrder(StatusCategory.CANCELED))
				.containsExactly(StatusCategory.CANCELED, StatusCategory.DONE);
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.IN_PROGRESS, noProgressNoCanceled))
				.contains(todo);
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.CANCELED, noProgressNoCanceled))
				.contains(done);

		// otherwise: the first active status of the Space
		List<SpaceStatusOption> onlyProgress = List.of(
				new SpaceStatusOption(uuid("p2"), StatusCategory.IN_PROGRESS, 2, false),
				new SpaceStatusOption(uuid("p1"), StatusCategory.IN_PROGRESS, 1, false));
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.TODO, onlyProgress)).contains(uuid("p1"));
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.CANCELED, onlyProgress)).contains(uuid("p1"));

		// nothing active: empty (the migration keeps the parent's status)
		assertThat(ResolutionMigrationMapping.pickStatus(StatusCategory.DONE,
				List.of(new SpaceStatusOption(done, StatusCategory.DONE, 0, true)))).isEmpty();
	}

	// ---- marker texts ----

	@Test
	void markerTextsAreLocalizedAndUkIsStored() {
		assertThat(ResolutionMigrationMapping.STORED_LOCALE.getLanguage()).isEqualTo("uk");
		assertThat(UK.marker()).isEqualTo("[Перенесено з резолюції]");
		assertThat(EN.marker()).isEqualTo("[Migrated from a resolution]");
		assertThat(UK.summaryText(ResolutionState.ACCEPTED))
				.isEqualTo("[Перенесено з резолюції] Стан резолюції на момент перенесення: «Прийнята».");
		assertThat(EN.summaryText(ResolutionState.NEW))
				.isEqualTo("[Migrated from a resolution] Resolution state at migration: “New”.");
	}

	@Test
	void submittedIsFlaggedInTheSummary() {
		assertThat(UK.summaryText(ResolutionState.SUBMITTED)).isEqualTo("[Перенесено з резолюції] Стан резолюції на"
				+ " момент перенесення: «На перевірці». Результат подано, очікує прийняття автором.");
		assertThat(EN.summaryText(ResolutionState.SUBMITTED)).endsWith("awaits acceptance by the author.");
		for (ResolutionState state : ResolutionState.values()) {
			if (state != ResolutionState.SUBMITTED) {
				assertThat(UK.summaryText(state)).doesNotContain("очікує прийняття");
			}
		}
	}

	@Test
	void historyEntriesLabelCompletionNotesReworkCommentsAndCascades() {
		UUID actor = uuid("actor");
		assertThat(UK.historyText(new SourceHistory(0, actor, null, ResolutionState.NEW, null, T0, null)))
				.isEqualTo("[Перенесено з резолюції] Резолюцію видано, стан «Нова».");
		assertThat(UK.historyText(new SourceHistory(2, actor, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED,
				"Done, see the minutes", T0, null))).isEqualTo("[Перенесено з резолюції] Стан змінено: «Виконується» →"
						+ " «На перевірці».\nЗвіт про виконання:\nDone, see the minutes");
		assertThat(UK.historyText(new SourceHistory(3, actor, ResolutionState.SUBMITTED, ResolutionState.REWORK,
				"Add the budget", T0, null))).endsWith("«На доопрацюванні».\nКоментар до доопрацювання:\nAdd the budget");
		assertThat(UK.historyText(new SourceHistory(4, actor, ResolutionState.NEW, ResolutionState.CANCELED,
				"Not needed", T0, uuid("ancestor")))).isEqualTo("[Перенесено з резолюції] Стан змінено: «Нова» →"
						+ " «Скасована». (скасовано разом із попередньою резолюцією ланцюжка)\nКоментар:\nNot needed");
		assertThat(EN.historyText(new SourceHistory(2, actor, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED,
				"x", T0, null))).isEqualTo("[Migrated from a resolution] State changed: “In progress” → “Submitted”."
						+ "\nCompletion note:\nx");
	}

	@Test
	void redirectedFromNamesTheSourceTitle() {
		assertThat(UK.redirectedFromText("Prepare the fake report"))
				.isEqualTo("[Перенесено з резолюції] Переадресовано з «Prepare the fake report».");
		assertThat(EN.redirectedFromText("Prepare the fake report"))
				.isEqualTo("[Migrated from a resolution] Redirected from “Prepare the fake report”.");
	}

	@Test
	void everyMarkerKeyExistsInUkAndEn() {
		var control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
		Set<String> uk = keys(ResourceBundle.getBundle("messages", Locale.forLanguageTag("uk"), control));
		Set<String> en = keys(ResourceBundle.getBundle("messages", Locale.ENGLISH, control));
		assertThat(uk).isNotEmpty().isEqualTo(en);
		for (ResolutionState state : ResolutionState.values()) {
			assertThat(uk).contains("tasks.migration.resolution.state." + state.name());
		}
	}

	private static Set<String> keys(ResourceBundle bundle) {
		return Collections.list(bundle.getKeys()).stream().filter(k -> k.startsWith("tasks.migration.resolution."))
				.collect(java.util.stream.Collectors.toSet());
	}

	// ---- title, document, ids ----

	@Test
	void titleIsTheFirstNonBlankLineCutTo200Characters() {
		assertThat(UK.title("  \r\n\t Prepare the fake report \r\nwith details")).isEqualTo("Prepare the fake report");
		assertThat(UK.title(" \n ")).isEqualTo("Резолюція");
		assertThat(EN.title(null)).isEqualTo("Resolution");
		String exact = "a".repeat(200);
		assertThat(UK.title(exact)).isEqualTo(exact);
		String title = UK.title("б".repeat(250));
		assertThat(title).hasSize(200).endsWith("б…");
		// code points, not UTF-16 units
		String emoji = "😀".repeat(201);
		String cut = UK.title(emoji);
		assertThat(cut.codePointCount(0, cut.length())).isEqualTo(200);
	}

	@Test
	void documentHasOneParagraphPerLine() {
		Map<String, Object> doc = ResolutionMigrationMapping.document("First\r\n\nThird");
		assertThat(doc).containsEntry("type", "doc");
		assertThat(doc.get("content")).isEqualTo(List.of(
				Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "First"))),
				Map.of("type", "paragraph"),
				Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "Third")))));
	}

	@Test
	void idsAreDeterministicAndDistinctPerKey() {
		assertThat(ResolutionMigrationMapping.id("comment:x:summary"))
				.isEqualTo(ResolutionMigrationMapping.id("comment:x:summary"))
				.isNotEqualTo(ResolutionMigrationMapping.id("comment:x:note"));
		// the 128 bits are exactly the MD5 digest, as PostgreSQL md5(...)::uuid
		assertThat(ResolutionMigrationMapping.id("").toString().replace("-", ""))
				.isEqualTo(md5Hex("resolution-migration:"));
	}

	private static String md5Hex(String s) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
					.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// ---- whole conversion ----

	@Test
	void convertsOneResolutionDeterministically() {
		UUID author = uuid("author");
		UUID executor = uuid("executor");
		UUID co = uuid("co");
		UUID task = uuid("task");
		UUID space = uuid("space");
		UUID progress = uuid("progress");
		SourceResolution r = new SourceResolution(uuid("res"), task, author, executor, Set.of(co, executor),
				"Collect fake feedback\nfrom two faculties", LocalDate.of(2026, 3, 1), uuid("source"),
				ResolutionState.SUBMITTED, "Collected", T0, author, T0.plusSeconds(300), executor, null, null,
				List.of(new SourceHistory(0, author, null, ResolutionState.NEW, null, T0, null),
						new SourceHistory(1, executor, ResolutionState.NEW, ResolutionState.IN_PROGRESS, null,
								T0.plusSeconds(60), null),
						new SourceHistory(2, executor, ResolutionState.IN_PROGRESS, ResolutionState.SUBMITTED,
								"Collected", T0.plusSeconds(300), null)),
				List.of(new SourceReminder("DUE_SOON", LocalDate.of(2026, 3, 1), T0.plusSeconds(100)),
						new SourceReminder("OVERDUE", LocalDate.of(2026, 3, 1), T0.plusSeconds(200))));
		ParentTask parent = new ParentTask(task, space, uuid("parent-status"), null, null, null);
		List<SpaceStatusOption> statuses = List.of(new SpaceStatusOption(progress, StatusCategory.IN_PROGRESS, 1,
				false), new SpaceStatusOption(uuid("todo"), StatusCategory.TODO, 0, false));

		var first = UK.migrate(r, parent, statuses, "Source resolution");
		var second = UK.migrate(r, parent, statuses, "Source resolution");
		assertThat(first).isEqualTo(second);

		assertThat(first.id()).isEqualTo(r.id());
		assertThat(first.parentTaskId()).isEqualTo(task);
		assertThat(first.spaceId()).isEqualTo(space);
		assertThat(first.statusId()).isEqualTo(progress);
		assertThat(first.title()).isEqualTo("Collect fake feedback");
		assertThat(first.deadline()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(first.creatorId()).isEqualTo(author);
		assertThat(first.issuedAt()).isEqualTo(T0);
		// executor once, first
		assertThat(first.assignees()).extracting(a -> a.userId()).containsExactly(executor, co);
		assertThat(first.assignees()).allSatisfy(a -> {
			assertThat(a.assignedBy()).isEqualTo(author);
			assertThat(a.assignedAt()).isEqualTo(T0);
		});
		// summary + 3 history entries + redirect; the completion note is carried by the SUBMITTED entry
		assertThat(first.comments()).hasSize(5);
		assertThat(first.comments().get(0).authorId()).isEqualTo(ResolutionMigrationMapping.SYSTEM_ACTOR);
		assertThat(first.comments().get(3).text()).endsWith("Звіт про виконання:\nCollected");
		assertThat(first.comments().get(4).text()).endsWith("Переадресовано з «Source resolution».");
		// DUE_SOON to both assignees, OVERDUE to both assignees and the author
		assertThat(first.reminders()).hasSize(5);
	}

	@Test
	void aResolutionOfASubtaskBecomesItsSiblingAndKeepsTheParentStatusWithoutActiveStatuses() {
		UUID grandParent = uuid("top");
		ParentTask parent = new ParentTask(uuid("sub"), uuid("space"), uuid("parent-status"), grandParent,
				Instant.parse("2026-04-01T00:00:00Z"), uuid("archiver"));
		SourceResolution r = new SourceResolution(uuid("r2"), parent.id(), uuid("a"), uuid("e"), Set.of(), "Do it",
				LocalDate.of(2026, 5, 1), null, ResolutionState.NEW, "Orphan note", T0, null, T0, null, null, null,
				List.of(), List.of());
		var migrated = UK.migrate(r, parent, List.of(), null);
		assertThat(migrated.parentTaskId()).isEqualTo(grandParent);
		assertThat(migrated.statusId()).isEqualTo(parent.statusId());
		assertThat(migrated.archivedAt()).isEqualTo(parent.archivedAt());
		assertThat(migrated.archivedBy()).isEqualTo(parent.archivedBy());
		// summary + the orphan completion note by the executor
		assertThat(migrated.comments()).hasSize(2);
		assertThat(migrated.comments().get(1).authorId()).isEqualTo(uuid("e"));
		assertThat(migrated.comments().get(1).text())
				.isEqualTo("[Перенесено з резолюції] Звіт про виконання:\nOrphan note");
	}

}
