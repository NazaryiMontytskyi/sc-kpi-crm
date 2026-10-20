package com.kpi.studentcouncil.crm.members.internal;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kpi.studentcouncil.crm.access.AccessService;
import com.kpi.studentcouncil.crm.audit.ActorProvider;
import com.kpi.studentcouncil.crm.audit.AuditAction;
import com.kpi.studentcouncil.crm.audit.AuditPublisher;
import com.kpi.studentcouncil.crm.files.FileService;
import com.kpi.studentcouncil.crm.identity.AccountService;
import com.kpi.studentcouncil.crm.identity.NewAccount;
import com.kpi.studentcouncil.crm.members.FacultyLookup;
import com.kpi.studentcouncil.crm.members.MemberArchiving;
import com.kpi.studentcouncil.crm.members.MemberEvent;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.CreateMemberRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.MemberRequest;
import com.kpi.studentcouncil.crm.members.internal.MemberDtos.SelfEditRequest;
import com.kpi.studentcouncil.crm.shared.error.BusinessRuleException;
import com.kpi.studentcouncil.crm.shared.error.ConflictException;
import com.kpi.studentcouncil.crm.shared.error.NotFoundException;

/**
 * Commands and queries of member profiles. The member id equals the account (user) id. Archiving is soft: the
 * profile is archived and sign-in is blocked through the identity module; nothing is ever deleted.
 */
@Service
class MemberService {

	static final String ENTITY_TYPE = "MemberProfile";

	private final MemberProfileRepository profiles;
	private final AccountService accounts;
	private final AccessService access;
	private final FacultyLookup faculties;
	private final FileService files;
	private final AuditPublisher audit;
	private final ActorProvider actors;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	MemberService(MemberProfileRepository profiles, AccountService accounts, AccessService access,
			FacultyLookup faculties, FileService files, AuditPublisher audit, ActorProvider actors,
			ApplicationEventPublisher events, Clock clock) {
		this.profiles = profiles;
		this.accounts = accounts;
		this.access = access;
		this.faculties = faculties;
		this.files = files;
		this.audit = audit;
		this.actors = actors;
		this.events = events;
		this.clock = clock;
	}

	/** Result of {@link #create}: the profile and the one-time temporary password. */
	record Created(MemberProfile profile, String temporaryPassword) {

		@Override
		public String toString() {
			return "Created[profile=" + profile.getUserId() + ", temporaryPassword=***]";
		}

	}

	/** The caller must already have checked {@code roles.assign} when {@code request.roleId()} is set. */
	@Transactional
	Created create(CreateMemberRequest request) {
		MemberData data = toData(request.facultyId(), request.lastName(), request.firstName(), request.middleName(),
				request.studyGroup(), request.telegram(), request.joinedAt(), request.avatarFileId(), request.bio());
		requireSelectableFaculty(data.facultyId(), null);
		requireFile(data.avatarFileId());
		NewAccount account = accounts.createAccount(request.login(), request.locale());
		MemberProfile saved = profiles.save(new MemberProfile(account.id(), data));
		if (request.roleId() != null) {
			access.grantRole(account.id(), request.roleId());
		}
		audit.publish(AuditAction.CREATE, ENTITY_TYPE, account.id(), null, saved.auditSnapshot());
		events.publishEvent(new MemberEvent(MemberEvent.Kind.CREATED, account.id()));
		return new Created(saved, account.temporaryPassword());
	}

	@Transactional
	MemberProfile update(UUID memberId, MemberRequest request) {
		MemberProfile profile = requireActive(memberId);
		MemberData data = toData(request.facultyId(), request.lastName(), request.firstName(), request.middleName(),
				request.studyGroup(), request.telegram(), request.joinedAt(), request.avatarFileId(), request.bio());
		requireSelectableFaculty(data.facultyId(), profile.getFacultyId());
		requireFile(data.avatarFileId());
		return change(profile, () -> profile.apply(data));
	}

	/** Self-service: the signed-in member edits own telegram, bio and avatar. */
	@Transactional
	MemberProfile updateSelf(SelfEditRequest request) {
		MemberProfile profile = requireActive(actors.currentActorId());
		String telegram = MemberRules.normalizeTelegram(request.telegram());
		String bio = MemberRules.blankToNull(request.bio());
		requireFile(request.avatarFileId());
		return change(profile, () -> profile.applySelf(telegram, bio, request.avatarFileId()));
	}

	@Transactional
	MemberProfile archive(UUID memberId) {
		MemberProfile profile = require(memberId);
		if (profile.isArchived()) {
			throw new ConflictException("ALREADY_ARCHIVED");
		}
		if (memberId.equals(actors.currentActorId())) {
			throw new BusinessRuleException("CANNOT_ARCHIVE_SELF");
		}
		// extension point: a Handover listener may veto by throwing
		events.publishEvent(new MemberArchiving(memberId));
		Map<String, Object> before = profile.auditSnapshot();
		profile.archive(actors.currentActorId(), clock.instant());
		MemberProfile saved = profiles.save(profile);
		accounts.archiveAccount(profile.getUserId());
		audit.publish(AuditAction.ARCHIVE, ENTITY_TYPE, memberId, before, saved.auditSnapshot());
		events.publishEvent(new MemberEvent(MemberEvent.Kind.ARCHIVED, memberId));
		return saved;
	}

	@Transactional
	MemberProfile restore(UUID memberId) {
		MemberProfile profile = require(memberId);
		if (!profile.isArchived()) {
			throw new ConflictException("NOT_ARCHIVED");
		}
		Map<String, Object> before = profile.auditSnapshot();
		profile.restore();
		MemberProfile saved = profiles.save(profile);
		accounts.restoreAccount(profile.getUserId());
		audit.publish(AuditAction.RESTORE, ENTITY_TYPE, memberId, before, saved.auditSnapshot());
		events.publishEvent(new MemberEvent(MemberEvent.Kind.RESTORED, memberId));
		return saved;
	}

	/** Issues a new random temporary password; the member must change it on next sign-in. */
	@Transactional
	String resetPassword(UUID memberId) {
		MemberProfile profile = requireActive(memberId);
		return accounts.resetPassword(profile.getUserId());
	}

	@Transactional(readOnly = true)
	MemberProfile get(UUID memberId) {
		return require(memberId);
	}

	private MemberProfile change(MemberProfile profile, Runnable mutation) {
		Map<String, Object> before = profile.auditSnapshot();
		mutation.run();
		MemberProfile saved = profiles.save(profile);
		audit.publish(AuditAction.UPDATE, ENTITY_TYPE, saved.getUserId(), before, saved.auditSnapshot());
		events.publishEvent(new MemberEvent(MemberEvent.Kind.UPDATED, saved.getUserId()));
		return saved;
	}

	private MemberProfile require(UUID memberId) {
		return profiles.findByUserId(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
	}

	private MemberProfile requireActive(UUID memberId) {
		MemberProfile profile = require(memberId);
		if (profile.isArchived()) {
			throw new ConflictException("MEMBER_ARCHIVED");
		}
		return profile;
	}

	/** A new reference needs an existing, active faculty; keeping the already stored one is always allowed. */
	private void requireSelectableFaculty(UUID facultyId, UUID currentFacultyId) {
		if (facultyId.equals(currentFacultyId)) {
			return;
		}
		if (!faculties.isSelectable(facultyId)) {
			throw new BusinessRuleException("FACULTY_NOT_SELECTABLE");
		}
	}

	private void requireFile(UUID fileId) {
		if (fileId != null) {
			files.require(fileId);
		}
	}

	private static MemberData toData(UUID facultyId, String lastName, String firstName, String middleName,
			String studyGroup, String telegram, java.time.LocalDate joinedAt, UUID avatarFileId, String bio) {
		return new MemberData(lastName.strip(), firstName.strip(), MemberRules.blankToNull(middleName), facultyId,
				studyGroup.strip(), MemberRules.normalizeTelegram(telegram), joinedAt, avatarFileId,
				MemberRules.blankToNull(bio));
	}

}
