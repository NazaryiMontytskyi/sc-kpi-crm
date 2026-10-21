package com.kpi.studentcouncil.crm.search.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.kpi.studentcouncil.crm.members.MemberTextProvider;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitEvent;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.tasks.ResolutionEvent;
import com.kpi.studentcouncil.crm.tasks.TaskEvent;
import com.kpi.studentcouncil.crm.tasks.TaskTextProvider;

/**
 * Keeps the index in step with the other modules. Events carry only ids, so every handler re-reads the current text
 * through the public provider of the owning module and then upserts the document, or deletes it when the entity is
 * archived or gone. Handlers run in the publisher's transaction (ADR-0007).
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

	@EventListener
	void on(TaskEvent event) {
		indexTask(event.id());
		if (event.kind() == TaskEvent.Kind.ARCHIVED || event.kind() == TaskEvent.Kind.RESTORED) {
			// resolutions of an archived task are hidden with it, and come back with it
			tasks.findResolutionIdsOfTask(event.id()).forEach(this::indexResolution);
		}
	}

	@EventListener
	void on(ResolutionEvent event) {
		indexResolution(event.id());
	}

	@EventListener
	void on(ArticleEvent event) {
		indexArticle(event.id());
	}

	@EventListener
	void on(MemberEvent event) {
		indexPerson(event.memberId());
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
		counts.put("resolutions", count(tasks.findActiveResolutionIds(), this::indexResolution));
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

	private void indexTask(UUID id) {
		tasks.findTask(id).filter(t -> !t.archived()).ifPresentOrElse(
				t -> index.upsert(SearchType.TASK, t.id(), t.title(),
						join(t.text(), t.labels() == null ? "" : String.join(" ", t.labels())), false, t.spaceId()),
				() -> index.delete(SearchType.TASK, id));
	}

	private void indexResolution(UUID id) {
		tasks.findResolution(id).filter(r -> !r.archived()).ifPresentOrElse(
				r -> index.upsert(SearchType.RESOLUTION, r.id(), r.title(), r.text(), false, r.taskId()),
				() -> index.delete(SearchType.RESOLUTION, id));
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
