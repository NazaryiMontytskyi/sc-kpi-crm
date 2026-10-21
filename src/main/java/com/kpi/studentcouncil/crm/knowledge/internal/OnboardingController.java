package com.kpi.studentcouncil.crm.knowledge.internal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.CreateOnboardingListRequest;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.OnboardingItemResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.OnboardingListResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.OnboardingPromptResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.ReadMarkResponse;
import com.kpi.studentcouncil.crm.knowledge.internal.KnowledgeDtos.UpdateOnboardingListRequest;
import com.kpi.studentcouncil.crm.knowledge.internal.OnboardingService.ListView;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Onboarding lists ("For newcomers"), personal read marks and the newcomer prompt. Reading needs {@code kb.view};
 * managing lists needs {@code onboarding.manage}. Read marks are private: every endpoint acts on the caller's own mark.
 */
@ApiV1
@Tag(name = "Knowledge onboarding")
class OnboardingController {

	private final OnboardingService service;

	OnboardingController(OnboardingService service) {
		this.service = service;
	}

	@GetMapping("/onboarding/lists")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "The caller's onboarding page: the general list plus the lists of the caller's departments",
			description = "Archived articles and restricted ones the caller may not see are left out; the order is kept. "
					+ "Each item carries the caller's own read mark.")
	List<OnboardingListResponse> mine() {
		return service.mine().stream().map(OnboardingController::response).toList();
	}

	@GetMapping("/onboarding/manage/lists")
	@PreAuthorize("hasAuthority('onboarding.manage')")
	@Operation(summary = "All onboarding lists for management (general first)",
			description = "Archived lists only with includeArchived=true.")
	List<OnboardingListResponse> manage(@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.listForManagement(includeArchived).stream().map(OnboardingController::response).toList();
	}

	@PostMapping("/onboarding/manage/lists")
	@PreAuthorize("hasAuthority('onboarding.manage')")
	@Operation(summary = "Create an onboarding list",
			description = "orgUnitId null creates the general list. 409 ONBOARDING_LIST_EXISTS when the scope already has "
					+ "an active list; 422 ONBOARDING_UNIT_INVALID / ONBOARDING_ARTICLE_INVALID for unknown, archived or "
					+ "invisible units or articles, or duplicates.")
	ResponseEntity<OnboardingListResponse> create(@Valid @RequestBody CreateOnboardingListRequest request) {
		ListView created = service.create(request.title(), request.orgUnitId(), request.articleIds());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/onboarding/manage/lists/" + created.list().getId())).body(response(created));
	}

	@PutMapping("/onboarding/manage/lists/{id}")
	@PreAuthorize("hasAuthority('onboarding.manage')")
	@Operation(summary = "Edit title and the ordered articles of a list (replaces the whole order)",
			description = "409 ONBOARDING_LIST_ARCHIVED when archived.")
	OnboardingListResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOnboardingListRequest request) {
		return response(service.update(id, request.title(), request.articleIds()));
	}

	@PostMapping("/onboarding/manage/lists/{id}/archive")
	@PreAuthorize("hasAuthority('onboarding.manage')")
	@Operation(summary = "Archive an onboarding list (soft)")
	OnboardingListResponse archive(@PathVariable UUID id) {
		return response(service.archive(id));
	}

	@PostMapping("/onboarding/manage/lists/{id}/restore")
	@PreAuthorize("hasAuthority('onboarding.manage')")
	@Operation(summary = "Restore an archived onboarding list",
			description = "409 ONBOARDING_LIST_EXISTS when its scope has another active list.")
	OnboardingListResponse restore(@PathVariable UUID id) {
		return response(service.restore(id));
	}

	@GetMapping("/articles/{id}/read-mark")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Whether the caller has marked the article as read (the caller's own mark only)")
	ReadMarkResponse readMark(@PathVariable UUID id) {
		return new ReadMarkResponse(service.readMark(id).read());
	}

	@PutMapping("/articles/{id}/read-mark")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Mark the article as read by the caller (idempotent; private to the caller)")
	ReadMarkResponse markRead(@PathVariable UUID id) {
		return new ReadMarkResponse(service.markRead(id).read());
	}

	@PostMapping("/articles/{id}/read-mark/withdraw")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Withdraw the caller's read mark on the article (idempotent)")
	ReadMarkResponse unmarkRead(@PathVariable UUID id) {
		return new ReadMarkResponse(service.unmarkRead(id).read());
	}

	@GetMapping("/onboarding/prompt")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Whether the newcomer prompt should still be shown to the caller",
			description = "pending is true until the caller dismisses it once.")
	OnboardingPromptResponse prompt() {
		return new OnboardingPromptResponse(service.promptPending());
	}

	@PostMapping("/onboarding/prompt/dismiss")
	@PreAuthorize("hasAuthority('kb.view')")
	@Operation(summary = "Dismiss the newcomer prompt for the caller (idempotent)")
	OnboardingPromptResponse dismissPrompt() {
		service.dismissPrompt();
		return new OnboardingPromptResponse(false);
	}

	private static OnboardingListResponse response(ListView v) {
		OnboardingList l = v.list();
		return new OnboardingListResponse(l.getId(), l.getTitle(), l.getOrgUnitId(), v.orgUnitName(),
				v.items().stream().map(i -> new OnboardingItemResponse(i.articleId(), i.title(), i.restricted(), i.read()))
						.toList(),
				l.isArchived(), l.getUpdatedAt());
	}

}
