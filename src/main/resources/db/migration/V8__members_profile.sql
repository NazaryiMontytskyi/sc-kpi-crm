-- Members: MemberProfile, 1:1 with users (CONTEXT.md 4, 5.1). Profiles are archived, never deleted.
-- activity_level_id is a nullable placeholder reference, filled by the activity-level feature (INC-022).
-- avatar_file_id references the files module by id only (no cross-module foreign key).
CREATE TABLE member_profile (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id),
    last_name         VARCHAR(100)  NOT NULL,
    first_name        VARCHAR(100)  NOT NULL,
    middle_name       VARCHAR(100),
    faculty_id        UUID          NOT NULL REFERENCES faculty (id),
    study_group       VARCHAR(50)   NOT NULL,
    telegram          VARCHAR(64)   NOT NULL,
    joined_at         DATE          NOT NULL,
    activity_level_id UUID,
    avatar_file_id    UUID,
    bio               VARCHAR(4000),
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ   NOT NULL,
    updated_by        UUID,
    archived_at       TIMESTAMPTZ,
    archived_by       UUID,
    CONSTRAINT uq_member_profile_user UNIQUE (user_id),
    CONSTRAINT ck_member_profile_last_name CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT ck_member_profile_first_name CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT ck_member_profile_study_group CHECK (length(btrim(study_group)) > 0),
    CONSTRAINT ck_member_profile_telegram CHECK (length(btrim(telegram)) > 0)
);
CREATE INDEX ix_member_profile_faculty ON member_profile (faculty_id);
CREATE INDEX ix_member_profile_name ON member_profile (lower(last_name), lower(first_name));
