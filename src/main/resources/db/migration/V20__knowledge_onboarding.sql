-- Knowledge base: onboarding lists ("For newcomers"), personal read marks and the one-time newcomer prompt.
-- CONTEXT.md 5.4. Lists are archived (soft delete), never deleted.
CREATE TABLE kb_onboarding_list (
    id          UUID         PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    org_unit_id UUID         REFERENCES org_unit (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_kb_onboarding_list_title CHECK (length(btrim(title)) > 0)
);
-- At most one active list for the general scope (org_unit_id IS NULL) and one per department.
CREATE UNIQUE INDEX uq_kb_onboarding_list_scope
    ON kb_onboarding_list (coalesce(org_unit_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE archived_at IS NULL;

-- Articles of a list in display order. Archived articles stay stored (so restoring one puts it back in place);
-- they are filtered out when the list is read.
CREATE TABLE kb_onboarding_list_item (
    list_id    UUID    NOT NULL REFERENCES kb_onboarding_list (id),
    position   INTEGER NOT NULL,
    article_id UUID    NOT NULL REFERENCES kb_article (id),
    PRIMARY KEY (list_id, position)
);
CREATE INDEX ix_kb_onboarding_list_item_article ON kb_onboarding_list_item (article_id);

-- Personal "I have read this" mark: private to the user, never exposed to anyone else.
CREATE TABLE kb_article_read (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    article_id UUID        NOT NULL REFERENCES kb_article (id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    CONSTRAINT uq_kb_article_read UNIQUE (user_id, article_id)
);

-- A row means the user has seen or dismissed the newcomer prompt.
CREATE TABLE kb_onboarding_prompt (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);
