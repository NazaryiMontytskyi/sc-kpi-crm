package com.kpi.studentcouncil.crm.members.internal;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
	private final MemberDirectory directory;
	private final FacultyLookup faculties;
	private final AccountService users;
	private final ActivityLevelService activityLevels;

	MemberController(MemberService service, MemberDirectory directory, FacultyLookup faculties, AccountService users,
			ActivityLevelService activityLevels) {
		this.service = service;
		this.directory = directory;
		this.faculties = faculties;
		this.users = users;
		this.activityLevels = activityLevels;
	}

	@GetMapping
	@Operation(summary = "Member directory (paginated, filterable, sortable by lastName, firstName, joinedAt; default lastName)",
			description = "All filters are optional and combined with AND. q: case-insensitive text matched against "
					+ "last/first/middle name and Telegram (a leading @ is ignored; several words must all match). "
					+ "departmentId/divisionId: members with an active membership in the unit or any of its descendants. "
					+ "positionId: current holders. status: ACTIVE (default), ARCHIVED or ALL; ARCHIVED and ALL are "
					+ "honoured only for holders of members.archive, otherwise treated as ACTIVE. includeArchived=true is a "
					+ "deprecated alias of status=ALL. activityLevelId: id of an activity level definition (GET /activity-levels). Ties are broken by id, "
					+ "so paging is stable.")
	PageResponse<MemberSummary> list(@Valid PageParams page, @RequestParam(required = false) String q,
			@RequestParam(required = false) UUID facultyId, @RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID divisionId, @RequestParam(required = false) UUID positionId,
			@RequestParam(required = false) UUID activityLevelId,
			@RequestParam(required = false) MemberFilter.Status status,
			@RequestParam(defaultValue = "false") boolean includeArchived, Authentication authentication) {
		MemberFilter.Status requested = status != null ? status
				: includeArchived ? MemberFilter.Status.ALL : MemberFilter.Status.ACTIVE;
		MemberFilter.Status effective = has(authentication, ARCHIVE) ? requested : MemberFilter.Status.ACTIVE;
		String search = q == null || q.isBlank() ? null : q;
		MemberFilter filter = new MemberFilter(search, facultyId, departmentId, divisionId, positionId,
				activityLevelId, effective);
		Sort defaultSort = Sort.by(Sort.Direction.ASC, "lastName").and(Sort.by(Sort.Direction.ASC, "firstName"));
		Pageable pageable = page.toPageable(SORTABLE, defaultSort);
		// deterministic tiebreaker so paging is stable
		pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id")));
		return PageResponse.of(directory.search(filter, pageable), this::toSummary);
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
				p.getTelegram(), p.getJoinedAt(), p.getActivityLevelId(),
				activityLevels.findDefinition(p.getActivityLevelId()).map(MemberActivityLevelController::toRef).orElse(null),
				p.getAvatarFileId(), p.getBio(),
				p.isArchived(), p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt());
	}

	private MemberSummary toSummary(MemberProfile p) {
		return new MemberSummary(p.getUserId(), p.getLastName(), p.getFirstName(), p.getMiddleName(),
				facultyRef(p.getFacultyId()), p.getStudyGroup(), p.getTelegram(), p.getJoinedAt(),
				p.getAvatarFileId(), p.isArchived());
	}

}
