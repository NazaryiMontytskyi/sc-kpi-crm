-- Org: Position (a post in an OrgUnit, exists independently of the person) and Appointment (a person holding a
-- position, with history). CONTEXT.md 2.2, 3, 5.2. Appointment rows are never deleted: ending sets end_date.
CREATE TABLE org_position (
    id          UUID         PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    org_unit_id UUID         NOT NULL REFERENCES org_unit (id),
    is_head     BOOLEAN      NOT NULL DEFAULT FALSE,
    capacity    INTEGER      NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_org_position_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_org_position_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_org_position_head_single CHECK (NOT is_head OR capacity = 1)
);
CREATE INDEX ix_org_position_unit ON org_position (org_unit_id);
-- At most one active head position per unit: the unit head is derived from its active appointment.
CREATE UNIQUE INDEX uq_org_position_head ON org_position (org_unit_id) WHERE is_head AND archived_at IS NULL;

-- Roles bound to a position. No FK to role: the id is validated by the service (the access module owns the table).
CREATE TABLE org_position_role (
    position_id UUID NOT NULL REFERENCES org_position (id),
    role_id     UUID NOT NULL,
    PRIMARY KEY (position_id, role_id)
);

CREATE TABLE org_appointment (
    id          UUID        PRIMARY KEY,
    position_id UUID        NOT NULL REFERENCES org_position (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    start_date  DATE        NOT NULL,
    end_date    DATE,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID,
    CONSTRAINT ck_org_appointment_period CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_org_appointment_position ON org_appointment (position_id);
CREATE INDEX ix_org_appointment_user ON org_appointment (user_id);
-- A person holds a position at most once at a time.
CREATE UNIQUE INDEX uq_org_appointment_active ON org_appointment (position_id, user_id) WHERE end_date IS NULL;
