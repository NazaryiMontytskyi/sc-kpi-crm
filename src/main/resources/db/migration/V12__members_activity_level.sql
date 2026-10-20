-- Members: activity level scale (editable reference data, OPEN-Q3) and append-only change history (CONTEXT.md 5.1).
-- member_profile.activity_level_id (V8) stays a plain reference validated by the service.
CREATE TABLE activity_level_definition (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    label_uk    VARCHAR(100) NOT NULL,
    label_en    VARCHAR(100) NOT NULL,
    sort_order  INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_activity_level_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_activity_level_label_uk CHECK (length(btrim(label_uk)) > 0),
    CONSTRAINT ck_activity_level_label_en CHECK (length(btrim(label_en)) > 0)
);
CREATE UNIQUE INDEX uq_activity_level_code_active ON activity_level_definition (lower(code)) WHERE archived_at IS NULL;

CREATE TABLE activity_level_change (
    id            UUID          PRIMARY KEY,
    member_id     UUID          NOT NULL REFERENCES users (id),
    from_level_id UUID          REFERENCES activity_level_definition (id),
    to_level_id   UUID          NOT NULL REFERENCES activity_level_definition (id),
    comment       VARCHAR(1000),
    changed_by    UUID          NOT NULL,
    changed_at    TIMESTAMPTZ   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_at    TIMESTAMPTZ   NOT NULL,
    updated_by    UUID
);
CREATE INDEX ix_activity_level_change_member ON activity_level_change (member_id, changed_at DESC);

-- History is append-only: rows can never be changed or removed.
CREATE FUNCTION activity_level_change_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'activity_level_change is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_activity_level_change_immutable
    BEFORE UPDATE OR DELETE ON activity_level_change
    FOR EACH ROW EXECUTE FUNCTION activity_level_change_immutable();
