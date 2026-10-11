package com.kpi.studentcouncil.crm.members.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the member API. */
final class MemberDtos {

	static final String LOGIN_REGEX = "[A-Za-z0-9._-]{3,64}";

	private MemberDtos() {
	}

	/** Profile fields edited by HR ({@code PUT /members/{id}}). */
	record MemberRequest(@NotBlank @Size(max = 100) String lastName, @NotBlank @Size(max = 100) String firstName,
			@Size(max = 100) String middleName, @NotNull UUID facultyId, @NotBlank @Size(max = 50) String studyGroup,
			@NotBlank @Pattern(regexp = MemberRules.TELEGRAM_REGEX) String telegram, @NotNull LocalDate joinedAt,
			UUID avatarFileId, @Size(max = 4000) String bio) {
	}

	/** Account creation: login, profile fields and an optional initial role. */
	record CreateMemberRequest(@NotBlank @Pattern(regexp = LOGIN_REGEX) String login,
			@NotBlank @Size(max = 100) String lastName, @NotBlank @Size(max = 100) String firstName,
			@Size(max = 100) String middleName, @NotNull UUID facultyId, @NotBlank @Size(max = 50) String studyGroup,
			@NotBlank @Pattern(regexp = MemberRules.TELEGRAM_REGEX) String telegram, @NotNull LocalDate joinedAt,
			UUID avatarFileId, @Size(max = 4000) String bio, @Pattern(regexp = "uk|en") String locale, UUID roleId) {
	}

	/** Fields a member edits on their own profile. */
	record SelfEditRequest(@NotBlank @Pattern(regexp = MemberRules.TELEGRAM_REGEX) String telegram,
			@Size(max = 4000) String bio, UUID avatarFileId) {
	}

	record FacultyRef(UUID id, String code, String nameUk, String nameEn, boolean active) {
	}

	/** The member id equals the account (user) id. */
	record MemberResponse(UUID id, String login, String lastName, String firstName, String middleName,
			FacultyRef faculty, String studyGroup, String telegram, LocalDate joinedAt, UUID activityLevelId,
			UUID avatarFileId, String bio, boolean archived, Instant archivedAt, Instant createdAt,
			Instant updatedAt) {
	}

	record MemberSummary(UUID id, String lastName, String firstName, String middleName, FacultyRef faculty,
			String studyGroup, String telegram, LocalDate joinedAt, UUID avatarFileId, boolean archived) {
	}

	/** Returned once on creation. The password is never stored in clear nor logged; toString masks it. */
	record CreatedMemberResponse(MemberResponse member, String temporaryPassword) {

		@Override
		public String toString() {
			return "CreatedMemberResponse[member=" + member.id() + ", temporaryPassword=***]";
		}

	}

	record TemporaryPasswordResponse(String temporaryPassword) {

		@Override
		public String toString() {
			return "TemporaryPasswordResponse[temporaryPassword=***]";
		}

	}

}
