-- Knowledge base: Article (page) with hierarchy, tags, attachments and extracted references. CONTEXT.md 5.4.
-- Articles are archived (soft delete), never deleted. Versions (snapshots) arrive with a later migration.
CREATE TABLE kb_article (
    id                UUID         PRIMARY KEY,
    title             VARCHAR(300) NOT NULL,
    content           JSONB        NOT NULL,
    parent_article_id UUID         REFERENCES kb_article (id),
    restricted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INTEGER      NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        UUID,
    archived_at       TIMESTAMPTZ,
    archived_by       UUID,
    CONSTRAINT ck_kb_article_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_kb_article_not_own_parent CHECK (parent_article_id IS NULL OR parent_article_id <> id)
);
CREATE INDEX ix_kb_article_parent ON kb_article (parent_article_id);

-- Tags are free-form, stored lower-cased.
CREATE TABLE kb_article_tag (
    article_id UUID        NOT NULL REFERENCES kb_article (id),
    tag        VARCHAR(50) NOT NULL,
    PRIMARY KEY (article_id, tag)
);
CREATE INDEX ix_kb_article_tag_tag ON kb_article_tag (tag);

-- Attachments are files of the files module, in display order.
CREATE TABLE kb_article_attachment (
    article_id UUID    NOT NULL REFERENCES kb_article (id),
    position   INTEGER NOT NULL,
    file_id    UUID    NOT NULL REFERENCES file_object (id),
    PRIMARY KEY (article_id, position)
);

-- Links and @mentions found in the content: kept in step with it on every save. The target lives in another
-- module (tasks, members) or is another article, so there is deliberately no foreign key.
CREATE TABLE kb_article_reference (
    article_id  UUID        NOT NULL REFERENCES kb_article (id),
    ref_type    VARCHAR(10) NOT NULL,
    ref_id      UUID        NOT NULL,
    PRIMARY KEY (article_id, ref_type, ref_id),
    CONSTRAINT ck_kb_article_reference_type CHECK (ref_type IN ('ARTICLE', 'TASK', 'PERSON'))
);
CREATE INDEX ix_kb_article_reference_target ON kb_article_reference (ref_type, ref_id);
