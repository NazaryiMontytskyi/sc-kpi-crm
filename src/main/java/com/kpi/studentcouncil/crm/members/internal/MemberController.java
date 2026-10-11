package com.kpi.studentcouncil.crm.members.internal;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kpi.studentcouncil.crm.identity.AccountService;
import com.kpi.studentcouncil.crm.members.FacultyInfo;
import com.kpi.studentcouncil.crm.members.FacultyLookup;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.CreateMemberRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.CreatedMemberResponse;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.FacultyRef;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.MemberRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.MemberResponse;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.MemberSummary;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.SelfEditRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.TemporaryPasswordResponse;
import com.kpi.studentcouncil.crm.shared.pagination.PageParams;
import com.kpi.studentcouncil.crm.shared.pagination.PageResponse;
import com.kpi.studentcouncil.crm.shared.web.ApiV1;

/**
 * Member profile API. Reads need authentication only (openness by default); mutations need the permissions of
 * CONTEXT.md 5.2. The member id is the account (user) id.
 */
@ApiV1
@RequestMapping("/members")
@Tag(name = "Members")
class MemberController {

	private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "joinedAt");
	private static final String ARCHIVE = "members.archive";
	private static final String ASSIGN_ROLES = "roles.assign";

	private final MemberService service;
	private final FacultyLookup faculties;
	private final AccountService users;

	MemberController(MemberService service, FacultyLookup faculties, AccountService users) {
		this.service = service;
		this.faculties = faculties;
		this.users = users;
	}

	@GetMapping
	@Operation(summary = "List members (paginated, sortable by lastName, firstName, joinedAt; default lastName)",
			description = "Active members only. includeArchived=true is honoured only for holders of "
					+ "members.archive. Directory filters are added in a later increment.")
	PageResponse<MemberSummary> list(@Valid PageParams page,
			@RequestParam(defaultValue = "false") boolean includeArchived, Authentication authentication) {
		boolean archivedAllowed = includeArchived && has(authentication, ARCHIVE);
		Sort defaultSort = Sort.by(Sort.Direction.ASC, "lastName").and(Sort.by(Sort.Direction.ASC, "firstName"));
		return PageResponse.of(service.list(archivedAllowed, page.toPageable(SORTABLE, defaultSort)),
				this::toSummary);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get one member profile, including an archived one")
	MemberResponse get(@PathVariable UUID id) {
		return toResponse(service.get(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('members.create')")
	@Operation(summary = "Create an account and profile",
			description = "Generates a random temporary password (returned once, the account must change it on "
					+ "first sign-in). An initial roleId additionally requires roles.assign.")
	ResponseEntity<CreatedMemberResponse> create(@Valid @RequestBody CreateMemberRequest request,
			Authentication authentication) {
		if (request.roleId() != null && !has(authentication, ASSIGN_ROLES)) {
			throw new AccessDeniedException("roles.assign required to set an initial role");
		}
		MemberService.Created created = service.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/members/" + created.profile().getUserId()))
				.cacheControl(CacheControl.noStore())
				.body(new CreatedMemberResponse(toResponse(created.profile()), created.temporaryPassword()));
	}

	@PutMapping("/me")
	@Operation(summary = "Edit own telegram, bio and avatar",
			description = "Any signed-in member; the avatar is a file id from the files module.")
	MemberResponse updateSelf(@Valid @RequestBody SelfEditRequest request) {
		return toResponse(service.updateSelf(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('members.edit')")
	@Operation(summary = "Edit a member profile",
			description = "The faculty must be active unless it is the one already stored. 409 for an archived member.")
	MemberResponse update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
		return toResponse(service.update(id, request));
	}

	@PostMapping("/{id}/archive")
	@PreAuthorize("hasAuthority('members.archive')")
	@Operation(summary = "Archive a member (soft) and block sign-in",
			description = "Open sessions stop working immediately. All attribution is kept. "
					+ "A member cannot archive themselves.")
	MemberResponse archive(@PathVariable UUID id) {
		return toResponse(service.archive(id));
	}

	@PostMapping("/{id}/restore")
	@PreAuthorize("hasAuthority('members.archive')")
	@Operation(summary = "Restore an archived member and re-enable sign-in")
	MemberResponse restore(@PathVariable UUID id) {
		return toResponse(service.restore(id));
	}

	@PostMapping("/{id}/reset-password")
	@PreAuthorize("hasAuthority('members.edit')")
	@Operation(summary = "Reset the password to a new random temporary one",
			description = "Returned once; the member must change it on next sign-in. [ASSUMPTION] governed by "
					+ "members.edit because CONTEXT.md 5.2 defines no dedicated permission.")
	ResponseEntity<TemporaryPasswordResponse> resetPassword(@PathVariable UUID id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new TemporaryPasswordResponse(service.resetPassword(id)));
	}

	private static boolean has(Authentication authentication, String authority) {
		return authentication != null
				&& authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
	}

	private FacultyRef facultyRef(UUID id) {
		return faculties.resolve(id).map(MemberController::toRef).orElse(null);
	}

	private static FacultyRef toRef(FacultyInfo f) {
		return new FacultyRef(f.id(), f.code(), f.nameUk(), f.nameEn(), f.active());
	}

	private MemberResponse toResponse(MemberProfile p) {
		return new MemberResponse(p.getUserId(), users.findLogin(p.getUserId()).orElse(null), p.getLastName(),
				p.getFirstName(), p.getMiddleName(), facultyRef(p.getFacultyId()), p.getStudyGroup(),
				p.getTelegram(), p.getJoinedAt(), p.getActivityLevelId(), p.getAvatarFileId(), p.getBio(),
				p.isArchived(), p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt());
	}

	private MemberSummary toSummary(MemberProfile p) {
		return new MemberSummary(p.getUserId(), p.getLastName(), p.getFirstName(), p.getMiddleName(),
				facultyRef(p.getFacultyId()), p.getStudyGroup(), p.getTelegram(), p.getJoinedAt(),
				p.getAvatarFileId(), p.isArchived());
	}

}
