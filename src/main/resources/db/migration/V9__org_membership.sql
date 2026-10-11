-- Org: Membership, a person's (user's) membership in an OrgUnit, M:N (CONTEXT.md 2.2, 3).
-- since/until are calendar dates (Europe/Kyiv day); until IS NULL means the membership is active.
-- Rows are never deleted: ending a membership sets until and keeps the history.
CREATE TABLE org_membership (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    org_unit_id UUID        NOT NULL REFERENCES org_unit (id),
    since       DATE        NOT NULL,
    until       DATE,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID,
    CONSTRAINT ck_org_membership_period CHECK (until IS NULL OR until >= since)
);
CREATE INDEX ix_org_membership_user ON org_membership (user_id);
CREATE INDEX ix_org_membership_unit ON org_membership (org_unit_id);
-- A person has at most one active membership per unit (but may have several in different units).
CREATE UNIQUE INDEX uq_org_membership_active ON org_membership (user_id, org_unit_id) WHERE until IS NULL;
