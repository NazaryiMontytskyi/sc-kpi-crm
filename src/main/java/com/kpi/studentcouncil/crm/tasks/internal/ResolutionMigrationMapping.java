package com.kpi.studentcouncil.crm.tasks.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import com.kpi.studentcouncil.crm.tasks.StatusCategory;

/**
 * Conversion of one legacy resolution into one subtask (TASK_TRACKING_FEATURE.md 5.8, ADR-0010). Pure, deterministic
 * and free of side effects.
 *
 * <p>This class is the executable specification of the Flyway migration {@code V28__tasks_resolutions_to_subtasks.sql}.
 * The migration itself is plain SQL so it never depends on application code that is removed later with the resolutions
 * (RR-1..RR-5); {@code ResolutionMigrationIntegrationTests} runs the migration on fake data and proves that every row
 * it writes equals what this class computes for the same input.
 *
 * <p>The migration has no request locale; marker texts are stored in the default locale {@code uk}. The texts live in
 * {@code messages_*.properties} under {@code tasks.migration.resolution.*} (uk and en) and are mirrored as literals in
 * the SQL.
 */
final class ResolutionMigrationMapping {

	/** The system actor (nil UUID), author of the generated summary comment. */
	static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

	/** Locale of the stored marker texts: the product default. */
	static final Locale STORED_LOCALE = Locale.forLanguageTag("uk");

	/** Maximum length of a derived subtask title, in characters (code points). */
	static final int TITLE_MAX = 200;

	private static final String KEY = "tasks.migration.resolution.";

	private static final String ID_PREFIX = "resolution-migration:";

	private static final String TRIM_CHARS = " \t\r";

	private final ResourceBundle bundle;

	private final Locale locale;

	private ResolutionMigrationMapping(ResourceBundle bundle, Locale locale) {
		this.bundle = bundle;
		this.locale = locale;
	}

	/** Mapping with the marker texts of {@code locale} (no fallback to the JVM locale). */
	static ResolutionMigrationMapping forLocale(Locale locale) {
		ResourceBundle bundle = ResourceBundle.getBundle("messages", locale,
				ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
		return new ResolutionMigrationMapping(bundle, locale);
	}

	/** Mapping with the texts the migration stores ({@link #STORED_LOCALE}). */
	static ResolutionMigrationMapping stored() {
		return forLocale(STORED_LOCALE);
	}

	// ---- input and output ----

	/** One entry of {@code resolution_history}. {@code from} is null for the creation entry. */
	record SourceHistory(int position, UUID actorId, ResolutionState from, ResolutionState to, String comment,
			Instant at, UUID causedByResolutionId) {
	}

	/** One row of {@code resolution_reminder}. */
	record SourceReminder(String threshold, LocalDate dueDate, Instant sentAt) {
	}

	/** One row of {@code resolution} with its co-executors, history and sent reminders. */
	record SourceResolution(UUID id, UUID taskId, UUID authorId, UUID executorId, Set<UUID> coExecutorIds,
			String instruction, LocalDate dueDate, UUID parentResolutionId, ResolutionState state,
			String completionNote, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy,
			Instant archivedAt, UUID archivedBy, List<SourceHistory> history, List<SourceReminder> reminders) {
	}

	/** The task the resolution belongs to. {@code parentTaskId} is null for a top-level task. */
	record ParentTask(UUID id, UUID spaceId, UUID statusId, UUID parentTaskId, Instant archivedAt, UUID archivedBy) {
	}

	/** A status of the parent's Space. */
	record SpaceStatusOption(UUID id, StatusCategory category, int position, boolean archived) {
	}

	record MigratedAssignee(UUID id, UUID userId, UUID assignedBy, Instant assignedAt) {
	}

	/** A comment on the subtask; {@code text} becomes a TipTap document, one paragraph per line. */
	record MigratedComment(UUID id, UUID authorId, Instant at, String text) {

		Map<String, Object> body() {
			return document(text);
		}
	}

	record MigratedReminder(UUID userId, String threshold, LocalDate deadline, Instant sentAt) {
	}

	/** The subtask and everything written for it. */
	record MigratedSubtask(UUID id, UUID parentTaskId, UUID spaceId, String title, Map<String, Object> description,
			UUID statusId, LocalDate deadline, UUID creatorId, Instant issuedAt, Instant createdAt, UUID createdBy,
			Instant updatedAt, UUID updatedBy, Instant archivedAt, UUID archivedBy, List<MigratedAssignee> assignees,
			List<MigratedComment> comments, List<MigratedReminder> reminders) {
	}

	// ---- the conversion ----

	/**
	 * Converts one resolution.
	 *
	 * @param statuses              the statuses of the parent's Space (archived ones included)
	 * @param redirectedFromInstruction instruction of the resolution this one was redirected from, null if none
	 */
	MigratedSubtask migrate(SourceResolution r, ParentTask parent, List<SpaceStatusOption> statuses,
			String redirectedFromInstruction) {
		Objects.requireNonNull(r, "resolution");
		Objects.requireNonNull(parent, "parent");
		// one level only: a resolution of a subtask becomes a sibling of it
		UUID parentTaskId = parent.parentTaskId() != null ? parent.parentTaskId() : parent.id();
		UUID statusId = pickStatus(targetCategory(r.state()), statuses).orElse(parent.statusId());
		Instant archivedAt = r.archivedAt() != null ? r.archivedAt() : parent.archivedAt();
		UUID archivedBy = r.archivedAt() != null ? r.archivedBy() : parent.archivedBy();

		List<UUID> assigneeIds = assigneeUserIds(r);
		List<MigratedAssignee> assignees = assigneeIds.stream()
				.map(u -> new MigratedAssignee(id("assignee:" + r.id() + ":" + u), u, r.authorId(), r.createdAt()))
				.toList();

		List<MigratedComment> comments = new ArrayList<>();
		comments.add(new MigratedComment(id("comment:" + r.id() + ":summary"), SYSTEM_ACTOR, r.updatedAt(),
				summaryText(r.state())));
		for (SourceHistory h : r.history()) {
			comments.add(new MigratedComment(id("comment:" + r.id() + ":history:" + h.position()), h.actorId(), h.at(),
					historyText(h)));
		}
		if (hasOrphanCompletionNote(r)) {
			comments.add(new MigratedComment(id("comment:" + r.id() + ":note"), r.executorId(), r.updatedAt(),
					completionNoteText(r.completionNote())));
		}
		if (r.parentResolutionId() != null) {
			comments.add(new MigratedComment(id("comment:" + r.id() + ":redirect"), r.authorId(), r.createdAt(),
					redirectedFromText(title(redirectedFromInstruction))));
		}

		Set<MigratedReminder> reminders = new LinkedHashSet<>();
		for (SourceReminder rem : r.reminders()) {
			for (UUID u : assigneeIds) {
				reminders.add(new MigratedReminder(u, rem.threshold(), rem.dueDate(), rem.sentAt()));
			}
			// the author also received "overdue" (DueDateReminderService); NT-2 keeps that for the creator
			if ("OVERDUE".equals(rem.threshold()) && !assigneeIds.contains(r.authorId())) {
				reminders.add(new MigratedReminder(r.authorId(), rem.threshold(), rem.dueDate(), rem.sentAt()));
			}
		}

		return new MigratedSubtask(r.id(), parentTaskId, parent.spaceId(), title(r.instruction()),
				document(r.instruction()), statusId, r.dueDate(), r.authorId(), r.createdAt(), r.createdAt(),
				r.createdBy(), r.updatedAt(), r.updatedBy(), archivedAt, archivedBy, assignees, List.copyOf(comments),
				List.copyOf(reminders));
	}

	// ---- rules ----

	/**
	 * Target status category of a legacy state: NEW -> TODO; IN_PROGRESS, REWORK, SUBMITTED -> IN_PROGRESS; ACCEPTED ->
	 * DONE; CANCELED, REDIRECTED -> CANCELED ([ASSUMPTION], spec 8 Q7).
	 */
	static StatusCategory targetCategory(ResolutionState state) {
		return switch (state) {
			case NEW -> StatusCategory.TODO;
			case IN_PROGRESS, REWORK, SUBMITTED -> StatusCategory.IN_PROGRESS;
			case ACCEPTED -> StatusCategory.DONE;
			case CANCELED, REDIRECTED -> StatusCategory.CANCELED;
		};
	}

	/**
	 * Categories tried in order for a target (ADR-0010): the target itself, then IN_PROGRESS -> TODO and CANCELED ->
	 * DONE. When none has an active status, {@link #pickStatus} takes the first active status of the Space.
	 */
	static List<StatusCategory> categoryOrder(StatusCategory target) {
		return switch (target) {
			case IN_PROGRESS -> List.of(StatusCategory.IN_PROGRESS, StatusCategory.TODO);
			case CANCELED -> List.of(StatusCategory.CANCELED, StatusCategory.DONE);
			case TODO, DONE -> List.of(target);
		};
	}

	/**
	 * The first active status (lowest position, then lowest id) of the first category of {@link #categoryOrder} that
	 * has one; otherwise the first active status of the Space; empty if the Space has no active status (the migration
	 * then keeps the parent's status).
	 */
	static Optional<UUID> pickStatus(StatusCategory target, List<SpaceStatusOption> statuses) {
		Comparator<SpaceStatusOption> order = Comparator.comparingInt(SpaceStatusOption::position)
				.thenComparing(s -> s.id().toString()); // string order = PostgreSQL uuid order
		for (StatusCategory category : categoryOrder(target)) {
			Optional<SpaceStatusOption> found = statuses.stream()
					.filter(s -> !s.archived() && s.category() == category).min(order);
			if (found.isPresent()) {
				return found.map(SpaceStatusOption::id);
			}
		}
		return statuses.stream().filter(s -> !s.archived()).min(order).map(SpaceStatusOption::id);
	}

	/** Executor first, then co-executors in id order; each user once. */
	static List<UUID> assigneeUserIds(SourceResolution r) {
		Set<UUID> ids = new LinkedHashSet<>();
		ids.add(r.executorId());
		r.coExecutorIds().stream().sorted(Comparator.comparing(UUID::toString)).forEach(ids::add);
		return List.copyOf(ids);
	}

	/**
	 * Title of the subtask: the first non-blank line of the instruction (trimmed of spaces, tabs and CR), cut to
	 * {@link #TITLE_MAX} characters with an ellipsis; the localized fallback if there is no such line.
	 */
	String title(String instruction) {
		if (instruction != null) {
			for (String line : instruction.split("\n", -1)) {
				String trimmed = trim(line);
				if (!trimmed.isEmpty()) {
					int length = trimmed.codePointCount(0, trimmed.length());
					if (length <= TITLE_MAX) {
						return trimmed;
					}
					return trimmed.substring(0, trimmed.offsetByCodePoints(0, TITLE_MAX - 1)) + "…";
				}
			}
		}
		return message("untitled");
	}

	/** A completion note is migrated on its own only if no SUBMITTED history entry already carries it. */
	static boolean hasOrphanCompletionNote(SourceResolution r) {
		if (r.completionNote() == null) {
			return false;
		}
		return r.history().stream().noneMatch(
				h -> h.to() == ResolutionState.SUBMITTED && r.completionNote().equals(h.comment()));
	}

	// ---- texts ----

	/** The marker every migrated comment starts with. */
	String marker() {
		return message("marker");
	}

	String stateLabel(ResolutionState state) {
		return message("state." + state.name());
	}

	/** Records the original state; SUBMITTED is flagged as awaiting acceptance. */
	String summaryText(ResolutionState state) {
		String text = marker() + " " + message("summary", stateLabel(state));
		if (state == ResolutionState.SUBMITTED) {
			text += " " + message("submitted");
		}
		return text;
	}

	/**
	 * One history entry: the transition, a cascade note, then the labelled comment (completion note for SUBMITTED,
	 * rework comment for REWORK, plain comment otherwise) on the following lines.
	 */
	String historyText(SourceHistory h) {
		String text = marker() + " " + (h.from() == null ? message("created", stateLabel(h.to()))
				: message("transition", stateLabel(h.from()), stateLabel(h.to())));
		if (h.causedByResolutionId() != null) {
			text += " " + message("cascade");
		}
		if (h.comment() != null) {
			String label = switch (h.to()) {
				case SUBMITTED -> message("completionNote");
				case REWORK -> message("reworkComment");
				default -> message("comment");
			};
			text += "\n" + label + "\n" + h.comment();
		}
		return text;
	}

	String completionNoteText(String note) {
		return marker() + " " + message("completionNote") + "\n" + note;
	}

	/** "Redirected from X" where X is the title of the sibling subtask the resolution was redirected from. */
	String redirectedFromText(String sourceTitle) {
		return marker() + " " + message("redirectedFrom", sourceTitle);
	}

	private String message(String key, Object... args) {
		String pattern = bundle.getString(KEY + key);
		// same rule as ResourceBundleMessageSource: MessageFormat only when there are arguments
		return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
	}

	// ---- helpers shared with the SQL ----

	/** TipTap document: one paragraph per line ({@code \r?\n}); an empty line is an empty paragraph. */
	static Map<String, Object> document(String text) {
		List<Object> paragraphs = new ArrayList<>();
		for (String line : text.split("\r?\n", -1)) {
			Map<String, Object> paragraph = new LinkedHashMap<>();
			paragraph.put("type", "paragraph");
			if (!line.isEmpty()) {
				paragraph.put("content", List.of(Map.of("type", "text", "text", line)));
			}
			paragraphs.add(paragraph);
		}
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("type", "doc");
		doc.put("content", paragraphs);
		return doc;
	}

	/** Deterministic id: the MD5 of {@code "resolution-migration:" + key}, as PostgreSQL {@code md5(...)::uuid}. */
	static UUID id(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("MD5").digest((ID_PREFIX + key).getBytes(StandardCharsets.UTF_8));
			long msb = 0;
			long lsb = 0;
			for (int i = 0; i < 8; i++) {
				msb = (msb << 8) | (digest[i] & 0xff);
				lsb = (lsb << 8) | (digest[i + 8] & 0xff);
			}
			return new UUID(msb, lsb);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("MD5 is not available", e);
		}
	}

	/** {@code btrim(s, ' \t\r')}. */
	private static String trim(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && TRIM_CHARS.indexOf(s.charAt(start)) >= 0) {
			start++;
		}
		while (end > start && TRIM_CHARS.indexOf(s.charAt(end - 1)) >= 0) {
			end--;
		}
		return s.substring(start, end);
	}

}
