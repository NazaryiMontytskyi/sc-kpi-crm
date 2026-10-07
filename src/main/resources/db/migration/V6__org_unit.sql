-- Org: the OrgUnit tree (CONTEXT.md 2.2, 3). parent_id is a plain self reference; type/parent rules live in the
-- application (OrgUnitPolicy), the database guards the invariants that must hold under concurrency.
CREATE TABLE org_unit (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    parent_id   UUID         REFERENCES org_unit (id),
    description VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_org_unit_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_org_unit_type CHECK (type IN ('LEADERSHIP', 'DEPARTMENT', 'DIVISION', 'WORKING_GROUP')),
    CONSTRAINT ck_org_unit_leadership_root CHECK (type <> 'LEADERSHIP' OR parent_id IS NULL),
    CONSTRAINT ck_org_unit_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_org_unit_parent ON org_unit (parent_id);
-- At most one active LEADERSHIP root.
CREATE UNIQUE INDEX uq_org_unit_single_leadership ON org_unit (type)
    WHERE type = 'LEADERSHIP' AND archived_at IS NULL;
