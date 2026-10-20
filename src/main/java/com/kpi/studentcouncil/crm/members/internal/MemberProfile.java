package com.kpi.studentcouncil.crm.members.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kpi.studentcouncil.crm.shared.domain.ArchivableEntity;

/** Profile of a council member, 1:1 with the account ({@code userId}). Never hard-deleted. */
@Entity
@Table(name = "member_profile")
class MemberProfile extends ArchivableEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "middle_name", length = 100)
	private String middleName;

	@Column(name = "faculty_id", nullable = false)
	private UUID facultyId;

	@Column(name = "study_group", nullable = false, length = 50)
	private String studyGroup;

	@Column(name = "telegram", nullable = false, length = 64)
	private String telegram;

	@Column(name = "joined_at", nullable = false)
	private LocalDate joinedAt;

	/** Placeholder reference, managed by the activity-level feature (INC-022). */
	@Column(name = "activity_level_id")
	private UUID activityLevelId;

	@Column(name = "avatar_file_id")
	private UUID avatarFileId;

	@Column(name = "bio", length = 4000)
	private String bio;

	protected MemberProfile() {
	}

	MemberProfile(UUID userId, MemberData data) {
		this.userId = userId;
		apply(data);
	}

	/** Replaces all editable fields. */
	void apply(MemberData data) {
		this.lastName = data.lastName();
		this.firstName = data.firstName();
		this.middleName = data.middleName();
		this.facultyId = data.facultyId();
		this.studyGroup = data.studyGroup();
		this.telegram = data.telegram();
		this.joinedAt = data.joinedAt();
		this.bio = data.bio();
		this.avatarFileId = data.avatarFileId();
	}

	/** Self-service fields. */
	void applySelf(String telegram, String bio, UUID avatarFileId) {
		this.telegram = telegram;
		this.bio = bio;
		this.avatarFileId = avatarFileId;
	}

	UUID getUserId() {
		return userId;
	}

	String getLastName() {
		return lastName;
	}

	String getFirstName() {
		return firstName;
	}

	String getMiddleName() {
		return middleName;
	}

	UUID getFacultyId() {
		return facultyId;
	}

	String getStudyGroup() {
		return studyGroup;
	}

	String getTelegram() {
		return telegram;
	}

	LocalDate getJoinedAt() {
		return joinedAt;
	}

	UUID getActivityLevelId() {
		return activityLevelId;
	}

	UUID getAvatarFileId() {
		return avatarFileId;
	}

	/** Only the activity-level feature changes this; every change is recorded in the history. */
	void setActivityLevelId(UUID activityLevelId) {
		this.activityLevelId = activityLevelId;
	}

	String getBio() {
		return bio;
	}

	/** Audit-safe view of the profile. */
	Map<String, Object> auditSnapshot() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("lastName", lastName);
		m.put("firstName", firstName);
		m.put("middleName", middleName);
		m.put("facultyId", facultyId == null ? null : facultyId.toString());
		m.put("studyGroup", studyGroup);
		m.put("telegram", telegram);
		m.put("joinedAt", joinedAt == null ? null : joinedAt.toString());
		m.put("avatarFileId", avatarFileId == null ? null : avatarFileId.toString());
		m.put("bio", bio);
		m.put("archivedAt", getArchivedAt() == null ? null : getArchivedAt().toString());
		return m;
	}

}
