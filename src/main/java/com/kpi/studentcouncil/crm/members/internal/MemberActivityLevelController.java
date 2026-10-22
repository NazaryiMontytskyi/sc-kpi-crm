package com.kpi.studentcouncil.crm.members.internal;

import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kpi.studentcouncil.crm.identity.AccountService;
import com.kpi.studentcouncil.crm.members.internal.ActivityLevelDtos.ChangeResponse;
import com.kpi.studentcouncil.crm.members.internal.ActivityLevelDtos.LevelRef;
import com.kpi.studentcouncil.crm.members.internal.ActivityLevelDtos.SetLevelRequest;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/** Per-member activity level: set (HR) and history (open to every signed-in user). */
@ApiV1
@RequestMapping("/members/{id}")
@Tag(name = "Members")
class MemberActivityLevelController {

	private final ActivityLevelService service;
	private final AccountService users;

	MemberActivityLevelController(ActivityLevelService service, AccountService users) {
		this.service = service;
		this.users = users;
	}

	@PutMapping("/activity-level")
	@PreAuthorize("hasAuthority('members.activity.set')")
	@Operation(summary = "Set the activity level of a member",
			description = "Appends a history row (append-only) and updates the current level. 422 when the level "
					+ "is archived/unknown or equals the current one; 409 for an archived member.")
	ChangeResponse setLevel(@PathVariable UUID id, @Valid @RequestBody SetLevelRequest request) {
		return toResponse(service.setLevel(id, request.levelId(), request.comment()));
	}

	@GetMapping("/activity-level-history")
	@Operation(summary = "Activity level history of a member, newest first (paginated)")
	PageResponse<ChangeResponse> history(@PathVariable UUID id, @ParameterObject @Valid PageParams page) {
		Pageable requested = page.toPageable(Set.of("changedAt"), Sort.by(Sort.Direction.DESC, "changedAt"));
		Pageable pageable = PageRequest.of(requested.getPageNumber(), requested.getPageSize(),
				requested.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
		return PageResponse.of(service.history(id, pageable), this::toResponse);
	}

	private ChangeResponse toResponse(ActivityLevelChange c) {
		return new ChangeResponse(c.getId(), c.getMemberId(), ref(c.getFromLevelId()), ref(c.getToLevelId()),
				c.getComment(), c.getChangedBy(), users.findLogin(c.getChangedBy()).orElse(null), c.getChangedAt());
	}

	private LevelRef ref(UUID levelId) {
		return service.findDefinition(levelId).map(MemberActivityLevelController::toRef).orElse(null);
	}

	static LevelRef toRef(ActivityLevelDefinition d) {
		return new LevelRef(d.getId(), d.getCode(), d.getLabelUk(), d.getLabelEn(), d.isArchived());
	}

}
