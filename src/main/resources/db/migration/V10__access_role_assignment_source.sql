-- Access: a role assignment may be granted on behalf of a domain record (e.g. a position appointment, ADR-0006).
-- source_type/source_id are both NULL for manual grants. Several active rows of the same (user, role) may coexist
-- when their sources differ, so the effective role stays until the last source is gone.
ALTER TABLE role_assignment
    ADD COLUMN source_type VARCHAR(30),
    ADD COLUMN source_id   UUID,
    ADD CONSTRAINT ck_role_assignment_source CHECK ((source_type IS NULL) = (source_id IS NULL));

DROP INDEX uq_role_assignment_active;
CREATE UNIQUE INDEX uq_role_assignment_active
    ON role_assignment (user_id, role_id, COALESCE(scope_org_unit_id, '00000000-0000-0000-0000-000000000000'),
                        COALESCE(source_type, ''), COALESCE(source_id, '00000000-0000-0000-0000-000000000000'))
    WHERE archived_at IS NULL;
CREATE INDEX ix_role_assignment_source ON role_assignment (source_type, source_id) WHERE archived_at IS NULL;
