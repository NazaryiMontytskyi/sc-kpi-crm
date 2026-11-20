package com.kpi.studentcouncil.crm.search.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.knowledge.ArticleEvent;
import com.kpi.studentcouncil.crm.knowledge.ArticleTextProvider;
import com.kpi.studentcouncil.crm.members.MemberEvent;
import com.kpi.studentcouncil.crm.members.MemberText;
import com.kpi.studentcouncil.crm.members.MemberTextProvider;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitEvent;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crm.tasks.TaskText;
import com.kpi.studentcouncil.crm.tasks.TaskTextProvider;

/**
 * Keeps the index in step with the other modules. Events carry only ids, so every handler re-reads the current text
 * through the public provider of the owning module and then upserts the document, or deletes it when the entity is
 * archived or gone. Handlers run in the publisher's transaction (ADR-0007).
 *
 * <p>A task document holds the title, the description, the labels, the full names of the active assignees (read
 * through {@link MemberTextProvider}) and the titles of the non-archived subtasks (RR-5). Resolutions are no longer
 * indexed (INC-080): resolution events are ignored, and the documents left over were deleted by migration V30.
 */
@Component
class SearchIndexer {

	static final String AUDIT_ENTITY_TYPE = "SearchIndex";

	private final SearchIndex index;
	private final TaskTextProvider tasks;
	private final MemberTextProvider members;
	private final ArticleTextProvider articles;
	private final OrgService orgUnits;
	private final AuditPublisher audit;

	SearchIndexer(SearchIndex index, TaskTextProvider tasks, MemberTextProvider members, ArticleTextProvider articles,
			OrgService orgUnits, AuditPublisher audit) {
		this.index = index;
		this.tasks = tasks;
		this.members = members;
		this.articles = articles;
		this.orgUnits = orgUnits;
		this.audit = audit;
	}

	/**
	 * Every task change re-indexes the task; a change of a subtask (created, renamed, archived, restored) also
	 * re-indexes its parent, whose document carries the subtask titles. Assign/unassign events refresh the assignee
	 * names, {@code SUBTASK_ADDED} refreshes the parent.
	 */
	@EventListener
	void on(TaskEvent event) {
		UUID parentId = indexTask(event.id());
		if (parentId != null) {
			indexTask(parentId);
		}
	}

	@EventListener
	void on(ArticleEvent event) {
		indexArticle(event.id());
	}

	/** A member change re-indexes the person and the tasks they are assigned to (their name is in those). */
	@EventListener
	void on(MemberEvent event) {
		indexPerson(event.memberId());
		tasks.findActiveTaskIdsAssignedTo(event.memberId()).forEach(this::indexTask);
	}

	@EventListener
	void on(OrgUnitEvent event) {
		orgUnits.find(event.id()).ifPresentOrElse(this::indexOrgUnit,
				() -> index.delete(SearchType.ORG_UNIT, event.id()));
	}

	/** Rebuilds the whole index from the public providers and audits the operation. */
	@Transactional
	Map<String, Integer> reindexAll() {
		index.deleteAll();
		Map<String, Integer> counts = new LinkedHashMap<>();
		counts.put("tasks", count(tasks.findActiveTaskIds(), this::indexTask));
		counts.put("resolutions", 0); // no longer indexed (INC-080); kept for the response shape
		counts.put("articles", count(articles.findActiveIds(), this::indexArticle));
		counts.put("people", count(members.findActiveIds(), this::indexPerson));
		int units = 0;
		for (OrgUnitInfo unit : orgUnits.findAll()) {
			if (unit.active()) {
				indexOrgUnit(unit);
				units++;
			}
		}
		counts.put("orgUnits", units);
		audit.publish(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, null, null, counts);
		return counts;
	}

	private static int count(List<UUID> ids, Consumer<UUID> indexer) {
		ids.forEach(indexer);
		return ids.size();
	}

	/** Upserts or deletes the task document; returns the parent task id of a subtask (null otherwise or if gone). */
	private UUID indexTask(UUID id) {
		TaskText task = tasks.findTask(id).orElse(null);
		if (task == null || task.archived()) {
			index.delete(SearchType.TASK, id);
		}
		else {
			index.upsert(SearchType.TASK, task.id(), task.title(), taskBody(task), false, task.spaceId());
		}
		return task == null ? null : task.parentId();
	}

	/** Description, labels, assignee full names and subtask titles, one part per line. */
	private String taskBody(TaskText task) {
		String body = join(task.text(), String.join(" ", task.labels()));
		body = join(body, String.join(", ", assigneeNames(task.assigneeIds())));
		return join(body, String.join("\n", task.subtaskTitles()));
	}

	/** Full names of the users that have a member profile; users without one (e.g. a bare admin) are skipped. */
	private List<String> assigneeNames(List<UUID> userIds) {
		return userIds.stream().map(members::findMember).flatMap(Optional::stream).map(MemberText::fullName)
				.filter(Objects::nonNull).filter(n -> !n.isBlank()).toList();
	}

	private void indexArticle(UUID id) {
		articles.findTextForIndexing(id).filter(a -> !a.archived()).ifPresentOrElse(
				a -> index.upsert(SearchType.ARTICLE, a.id(), a.title(),
						join(a.text(), a.tags() == null ? "" : String.join(" ", a.tags())), a.restricted(), null),
				() -> index.delete(SearchType.ARTICLE, id));
	}

	private void indexPerson(UUID id) {
		members.findMember(id).filter(m -> !m.archived()).ifPresentOrElse(
				m -> index.upsert(SearchType.PERSON, m.id(), m.fullName(), m.details(), false, null),
				() -> index.delete(SearchType.PERSON, id));
	}

	private void indexOrgUnit(OrgUnitInfo unit) {
		if (!unit.active()) {
			index.delete(SearchType.ORG_UNIT, unit.id());
			return;
		}
		String type = unit.type() == null ? "" : unit.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
		index.upsert(SearchType.ORG_UNIT, unit.id(), unit.name(), type, false, unit.parentId());
	}

	private static String join(String a, String b) {
		if (a == null || a.isBlank()) {
			return b == null ? "" : b;
		}
		return b == null || b.isBlank() ? a : a + "\n" + b;
	}

}
