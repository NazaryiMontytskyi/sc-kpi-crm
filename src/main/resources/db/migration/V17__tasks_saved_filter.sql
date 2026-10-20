-- Saved task filters: private to their owner (CONTEXT.md 5.3 task list). Removing a filter archives it (soft delete).
-- The criteria mirror the query parameters of the task list; space_id/status_id carry no foreign key because a filter
-- may outlive an archived space or status.
CREATE TABLE task_saved_filter (
    id               UUID         PRIMARY KEY,
    owner_id         UUID         NOT NULL REFERENCES users (id),
    name             VARCHAR(100) NOT NULL,
    space_id         UUID,
    status_id        UUID,
    executor_id      UUID,
    author_id        UUID,
    resolution_state VARCHAR(20),
    quick_view       VARCHAR(20),
    due_from         DATE,
    due_to           DATE,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       UUID,
    archived_at      TIMESTAMPTZ,
    archived_by      UUID,
    CONSTRAINT ck_task_saved_filter_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_task_saved_filter_state CHECK (resolution_state IS NULL OR resolution_state IN
        ('NEW', 'IN_PROGRESS', 'SUBMITTED', 'REWORK', 'ACCEPTED', 'REDIRECTED', 'CANCELED')),
    CONSTRAINT ck_task_saved_filter_view CHECK (quick_view IS NULL OR quick_view IN
        ('MY_RESOLUTIONS', 'ISSUED_BY_ME', 'OVERDUE'))
);
CREATE INDEX ix_task_saved_filter_owner ON task_saved_filter (owner_id);
CREATE UNIQUE INDEX ux_task_saved_filter_owner_name ON task_saved_filter (owner_id, lower(name))
    WHERE archived_at IS NULL;

CREATE TABLE task_saved_filter_label (
    filter_id UUID        NOT NULL REFERENCES task_saved_filter (id),
    label     VARCHAR(50) NOT NULL,
    PRIMARY KEY (filter_id, label)
);
