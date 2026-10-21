package com.kpi.studentcouncil.crm.knowledge.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/**
 * An ordered reading list for newcomers: the general one ({@code orgUnitId} null) or one for a department. The order
 * of {@code articleIds} is the display order; archived articles stay in it and are filtered when the list is read.
 */
@Entity
@Table(name = "kb_onboarding_list")
class OnboardingList extends ArchivableEntity {

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "org_unit_id", updatable = false)
	private UUID orgUnitId;

	@ElementCollection(fetch = FetchType.EAGER)
	@Fetch(FetchMode.SELECT)
	@BatchSize(size = 50)
	@CollectionTable(name = "kb_onboarding_list_item", joinColumns = @JoinColumn(name = "list_id"))
	@OrderColumn(name = "position")
	@Column(name = "article_id", nullable = false)
	private List<UUID> articleIds = new ArrayList<>();

	protected OnboardingList() {
	}

	OnboardingList(String title, UUID orgUnitId, List<UUID> articleIds) {
		this.title = title;
		this.orgUnitId = orgUnitId;
		this.articleIds = new ArrayList<>(articleIds);
	}

	String getTitle() {
		return title;
	}

	UUID getOrgUnitId() {
		return orgUnitId;
	}

	List<UUID> getArticleIds() {
		return List.copyOf(articleIds);
	}

	void edit(String title, List<UUID> articleIds) {
		this.title = title;
		this.articleIds.clear();
		this.articleIds.addAll(articleIds);
	}

	Map<String, Object> auditSnapshot() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("title", title);
		map.put("orgUnitId", orgUnitId == null ? null : orgUnitId.toString());
		map.put("articleIds", articleIds.stream().map(UUID::toString).toList());
		map.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return map;
	}

}
