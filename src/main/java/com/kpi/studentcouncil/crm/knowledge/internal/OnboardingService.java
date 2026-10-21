package com.kpi.studentcouncil.crm.knowledge.internal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.org.OrgQueryService;
import com.kpi.studentcouncil.crm.org.OrgService;
import com.kpi.studentcouncil.crm.org.OrgUnitInfo;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Onboarding lists ("For newcomers"), the caller's private read marks and the one-time newcomer prompt. Lists are
 * managed with {@code onboarding.manage} (checked in the controller); reading a list never shows archived articles or
 * restricted ones the caller may not see, and never reorders the rest. Read marks are only ever looked up for the
 * current actor.
 */
@Service
class OnboardingService {

	static final String ONBOARDING_LIST = "OnboardingList";
	static final int MAX_ARTICLES = 100;

	/** An article of a list as the caller sees it. */
	record Item(UUID articleId, String title, boolean restricted, boolean read) {
	}

	record ListView(OnboardingList list, String orgUnitName, List<Item> items) {
	}

	record ReadMark(boolean read) {
	}

	private final OnboardingListRepository lists;
	private final ArticleRepository articles;
	private final ArticleReadMarkRepository marks;
	private final OnboardingPromptRepository prompts;
	private final ArticleViewer viewer;
	private final OrgService orgUnits;
	private final OrgQueryService membership;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final Clock clock;

	OnboardingService(OnboardingListRepository lists, ArticleRepository articles, ArticleReadMarkRepository marks,
			OnboardingPromptRepository prompts, ArticleViewer viewer, OrgService orgUnits, OrgQueryService membership,
			AuditPublisher audit, ActorProvider actors, Clock clock) {
		this.lists = lists;
		this.articles = articles;
		this.marks = marks;
		this.prompts = prompts;
		this.viewer = viewer;
		this.orgUnits = orgUnits;
		this.membership = membership;
		this.audit = audit;
		this.actors = actors;
		this.clock = clock;
	}

	// ---- commands (onboarding.manage) ----

	@Transactional
	ListView create(String title, UUID orgUnitId, List<UUID> articleIds) {
		if (orgUnitId != null && !orgUnits.isActive(orgUnitId)) {
			throw new BusinessRuleException("ONBOARDING_UNIT_INVALID");
		}
		if (lists.existsActiveForScope(orgUnitId)) {
			throw new ConflictException("ONBOARDING_LIST_EXISTS");
		}
		List<UUID> ids = validatedArticles(articleIds);
		OnboardingList saved = saveChecked(new OnboardingList(title.strip(), orgUnitId, ids));
		audit.publish(AuditAction.CREATE, ONBOARDING_LIST, saved.getId(), null, saved.auditSnapshot());
		return view(saved);
	}

	/** Replaces title and the ordered article ids. */
	@Transactional
	ListView update(UUID id, String title, List<UUID> articleIds) {
		OnboardingList list = require(id);
		if (list.isArchived()) {
			throw new ConflictException("ONBOARDING_LIST_ARCHIVED");
		}
		List<UUID> ids = validatedArticles(articleIds);
		Map<String, Object> before = list.auditSnapshot();
		list.edit(title.strip(), ids);
		OnboardingList saved = saveChecked(list);
		audit.publish(AuditAction.UPDATE, ONBOARDING_LIST, id, before, saved.auditSnapshot());
		return view(saved);
	}

	@Transactional
	ListView archive(UUID id) {
		OnboardingList list = require(id);
		Map<String, Object> before = list.auditSnapshot();
		list.archive(actors.currentActorId(), clock.instant());
		OnboardingList saved = lists.save(list);
		audit.publish(AuditAction.ARCHIVE, ONBOARDING_LIST, id, before, saved.auditSnapshot());
		return view(saved);
	}

	@Transactional
	ListView restore(UUID id) {
		OnboardingList list = require(id);
		if (!list.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		if (lists.existsActiveForScope(list.getOrgUnitId())) {
			throw new ConflictException("ONBOARDING_LIST_EXISTS");
		}
		Map<String, Object> before = list.auditSnapshot();
		list.restore();
		OnboardingList saved = saveChecked(list);
		audit.publish(AuditAction.RESTORE, ONBOARDING_LIST, id, before, saved.auditSnapshot());
		return view(saved);
	}

	// ---- queries ----

	/** All lists for the manager screen (general first); archived ones only on request. */
	@Transactional(readOnly = true)
	List<ListView> listForManagement(boolean includeArchived) {
		return lists.findAllForManage(includeArchived).stream().map(this::view).toList();
	}

	/** The "For newcomers" page of the caller: the general list plus the lists of the caller's departments. */
	@Transactional(readOnly = true)
	List<ListView> mine() {
		List<UUID> unitIds = membership.unitIdsOfUser(actors.currentActorId());
		List<UUID> scope = unitIds.isEmpty() ? List.of(new UUID(0, 0)) : unitIds;
		return lists.findActiveForUnits(scope).stream()
				.filter(l -> l.getOrgUnitId() == null || orgUnits.isActive(l.getOrgUnitId())).map(this::view).toList();
	}

	// ---- own read marks ----

	@Transactional
	ReadMark markRead(UUID articleId) {
		requireVisible(articleId);
		UUID user = actors.currentActorId();
		if (!marks.isRead(user, articleId)) {
			marks.save(new ArticleReadMark(user, articleId));
		}
		return new ReadMark(true);
	}

	@Transactional
	ReadMark unmarkRead(UUID articleId) {
		requireVisible(articleId);
		marks.withdraw(actors.currentActorId(), articleId);
		return new ReadMark(false);
	}

	@Transactional(readOnly = true)
	ReadMark readMark(UUID articleId) {
		requireVisible(articleId);
		return new ReadMark(marks.isRead(actors.currentActorId(), articleId));
	}

	// ---- newcomer prompt ----

	/** True until the caller dismisses the prompt once. */
	@Transactional(readOnly = true)
	boolean promptPending() {
		return !prompts.existsByUserId(actors.currentActorId());
	}

	@Transactional
	void dismissPrompt() {
		UUID user = actors.currentActorId();
		if (!prompts.existsByUserId(user)) {
			prompts.save(new OnboardingPrompt(user));
		}
	}

	// ---- helpers ----

	/**
	 * Stored ids in their stored order, keeping only articles that are active and that the predicate accepts. Pure so the
	 * "archived articles disappear without breaking order" rule is unit-testable.
	 */
	static List<UUID> visibleInOrder(List<UUID> stored, Map<UUID, Article> byId, Predicate<Article> canSee) {
		List<UUID> out = new ArrayList<>();
		for (UUID id : stored) {
			Article a = byId.get(id);
			if (a != null && !a.isArchived() && canSee.test(a)) {
				out.add(id);
			}
		}
		return out;
	}

	private ListView view(OnboardingList list) {
		Map<UUID, Article> byId = articles.findByIdIn(list.getArticleIds()).stream()
				.collect(Collectors.toMap(Article::getId, a -> a));
		List<UUID> visible = visibleInOrder(list.getArticleIds(), byId, viewer::canSee);
		Set<UUID> read = visible.isEmpty() ? Set.of()
				: new HashSet<>(marks.readAmong(actors.currentActorId(), visible));
		List<Item> items = visible.stream().map(byId::get)
				.map(a -> new Item(a.getId(), a.getTitle(), a.isRestricted(), read.contains(a.getId()))).toList();
		String unitName = list.getOrgUnitId() == null ? null
				: orgUnits.find(list.getOrgUnitId()).map(OrgUnitInfo::name).orElse(null);
		return new ListView(list, unitName, items);
	}

	/** Article ids must be distinct, active and visible to the actor; the order is kept. */
	private List<UUID> validatedArticles(List<UUID> articleIds) {
		List<UUID> ids = articleIds == null ? List.of() : articleIds;
		Set<UUID> distinct = new LinkedHashSet<>(ids);
		if (distinct.size() != ids.size() || ids.size() > MAX_ARTICLES || ids.contains(null)) {
			throw new BusinessRuleException("ONBOARDING_ARTICLE_INVALID");
		}
		Map<UUID, Article> byId = articles.findByIdIn(ids).stream().collect(Collectors.toMap(Article::getId, a -> a));
		for (UUID id : ids) {
			Article a = byId.get(id);
			if (a == null || a.isArchived() || !viewer.canSee(a)) {
				throw new BusinessRuleException("ONBOARDING_ARTICLE_INVALID");
			}
		}
		return List.copyOf(ids);
	}

	private OnboardingList require(UUID id) {
		Optional<OnboardingList> found = lists.findById(id);
		return found.orElseThrow(() -> new NotFoundException(ONBOARDING_LIST, id));
	}

	private Article requireVisible(UUID articleId) {
		return articles.findById(articleId).filter(viewer::canSee)
				.orElseThrow(() -> new NotFoundException(ArticleService.ARTICLE, articleId));
	}

	/** The unique scope index is the last line of defence against two concurrent creates. */
	private OnboardingList saveChecked(OnboardingList list) {
		try {
			return lists.saveAndFlush(list);
		}
		catch (DataIntegrityViolationException e) {
			throw new ConflictException("ONBOARDING_LIST_EXISTS");
		}
	}

}
