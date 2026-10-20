-- Tasks: Space (container of tasks, optionally bound to an OrgUnit or general), its ordered configurable statuses
-- and custom field definitions. CONTEXT.md 5.3. Rows are archived (soft delete), never deleted.
CREATE TABLE task_space (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    org_unit_id UUID         REFERENCES org_unit (id),
    is_general  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_task_space_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_task_space_general CHECK (NOT is_general OR org_unit_id IS NULL)
);
CREATE INDEX ix_task_space_org_unit ON task_space (org_unit_id);

CREATE TABLE task_space_status (
    id          UUID         PRIMARY KEY,
    space_id    UUID         NOT NULL REFERENCES task_space (id),
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    position    INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_task_space_status_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_task_space_status_category CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELED'))
);
CREATE INDEX ix_task_space_status_space ON task_space_status (space_id);
CREATE UNIQUE INDEX uq_task_space_status_name ON task_space_status (space_id, lower(name)) WHERE archived_at IS NULL;

CREATE TABLE task_custom_field (
    id          UUID         PRIMARY KEY,
    space_id    UUID         NOT NULL REFERENCES task_space (id),
    name        VARCHAR(100) NOT NULL,
    field_type  VARCHAR(20)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_task_custom_field_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_task_custom_field_type CHECK (field_type IN ('TEXT', 'NUMBER', 'DATE', 'SELECT', 'PERSON'))
);
CREATE INDEX ix_task_custom_field_space ON task_custom_field (space_id);
CREATE UNIQUE INDEX uq_task_custom_field_name ON task_custom_field (space_id, lower(name)) WHERE archived_at IS NULL;

-- Options of a SELECT field, in display order.
CREATE TABLE task_custom_field_option (
    field_id UUID         NOT NULL REFERENCES task_custom_field (id),
    position INTEGER      NOT NULL,
    value    VARCHAR(100) NOT NULL,
    PRIMARY KEY (field_id, position)
);
