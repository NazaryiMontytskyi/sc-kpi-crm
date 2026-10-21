-- Search (INC-046, ADR-0007): denormalized index of tasks, resolutions, articles, people and org units. It is derived
-- data owned by the search module, rebuilt from domain events and by the admin reindex; no foreign keys to other
-- modules (ids only) and no audit columns of its own (the reindex and the source changes are audited).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE search_document (
    entity_type VARCHAR(20)  NOT NULL,
    entity_id   UUID         NOT NULL,
    title       VARCHAR(500) NOT NULL,
    body        TEXT         NOT NULL DEFAULT '',
    -- lower-cased title and "title body", computed by the application so matching does not depend on DB locale
    title_lc    TEXT         NOT NULL,
    search_text TEXT         NOT NULL,
    restricted  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- parent for navigation: the task of a resolution, the space of a task
    parent_id   UUID,
    indexed_at  TIMESTAMPTZ  NOT NULL,
    -- 'simple' keeps Ukrainian words as typed (prefix matching); 'english' adds stemming for English text
    tsv         TSVECTOR GENERATED ALWAYS AS (
                    to_tsvector('simple', search_text) || to_tsvector('english', search_text)
                ) STORED,
    CONSTRAINT pk_search_document PRIMARY KEY (entity_type, entity_id),
    CONSTRAINT ck_search_document_type CHECK (entity_type IN ('TASK', 'RESOLUTION', 'ARTICLE', 'PERSON', 'ORG_UNIT'))
);
CREATE INDEX ix_search_document_tsv ON search_document USING GIN (tsv);
CREATE INDEX ix_search_document_text_trgm ON search_document USING GIN (search_text gin_trgm_ops);
CREATE INDEX ix_search_document_title_trgm ON search_document USING GIN (title_lc gin_trgm_ops);
CREATE INDEX ix_search_document_parent ON search_document (parent_id);
