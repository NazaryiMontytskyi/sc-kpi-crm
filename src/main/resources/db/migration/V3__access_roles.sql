-- Access: roles and role assignments (CONTEXT.md 5.2). The hardcoded Admin role has no row in "role": it is the
-- reserved id 00000000-0000-0000-0000-000000000001 used in role_assignment.role_id (see ADR-0003).
CREATE TABLE role (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_role_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_role_not_admin_id CHECK (id <> '00000000-0000-0000-0000-000000000001'),
    CONSTRAINT ck_role_not_admin_name CHECK (lower(btrim(name)) <> 'admin')
);
CREATE UNIQUE INDEX uq_role_name_active ON role (lower(name)) WHERE archived_at IS NULL;

CREATE TABLE role_permission (
    role_id        UUID         NOT NULL REFERENCES role (id),
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE role_assignment (
    id                UUID        PRIMARY KEY,
    user_id           UUID        NOT NULL,
    role_id           UUID        NOT NULL,
    scope_org_unit_id UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ NOT NULL,
    updated_by        UUID,
    archived_at       TIMESTAMPTZ,
    archived_by       UUID
);
CREATE INDEX ix_role_assignment_user ON role_assignment (user_id) WHERE archived_at IS NULL;
CREATE UNIQUE INDEX uq_role_assignment_active
    ON role_assignment (user_id, role_id, COALESCE(scope_org_unit_id, '00000000-0000-0000-0000-000000000000'))
    WHERE archived_at IS NULL;
