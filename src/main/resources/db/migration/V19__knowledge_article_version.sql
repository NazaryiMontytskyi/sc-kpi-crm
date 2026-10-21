-- Knowledge base: immutable snapshot of an article on every save (CONTEXT.md 5.4). History is never rewritten:
-- restoring an old version appends a new one. created_at / created_by are the time and the editor of the save.
CREATE TABLE kb_article_version (
    id             UUID         PRIMARY KEY,
    article_id     UUID         NOT NULL REFERENCES kb_article (id),
    version_number INTEGER      NOT NULL,
    title          VARCHAR(300) NOT NULL,
    content        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     UUID,
    CONSTRAINT uq_kb_article_version UNIQUE (article_id, version_number)
);

-- Backfill: articles saved before versioning get a snapshot of their current state under their current number.
INSERT INTO kb_article_version (id, article_id, version_number, title, content, created_at, created_by, updated_at,
                                updated_by)
SELECT gen_random_uuid(), id, version, title, content, updated_at, updated_by, updated_at, updated_by
FROM kb_article;
