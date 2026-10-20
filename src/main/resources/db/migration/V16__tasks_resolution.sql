-- Resolutions: an assignment to carry out a task (CONTEXT.md 5.3). A task can have several resolutions; a redirect
-- creates a child resolution (parent_resolution_id). Resolutions are archived (soft delete), never deleted.
CREATE TABLE resolution (
    id                   UUID        PRIMARY KEY,
    task_id              UUID        NOT NULL REFERENCES task (id),
    author_id            UUID        NOT NULL REFERENCES users (id),
    executor_id          UUID        NOT NULL REFERENCES users (id),
    instruction          TEXT        NOT NULL,
    due_date             DATE        NOT NULL,
    parent_resolution_id UUID        REFERENCES resolution (id),
    state                VARCHAR(20) NOT NULL,
    completion_note      TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    created_by           UUID,
    updated_at           TIMESTAMPTZ NOT NULL,
    updated_by           UUID,
    archived_at          TIMESTAMPTZ,
    archived_by          UUID,
    CONSTRAINT ck_resolution_state CHECK (state IN
        ('NEW', 'IN_PROGRESS', 'SUBMITTED', 'REWORK', 'ACCEPTED', 'REDIRECTED', 'CANCELED')),
    CONSTRAINT ck_resolution_instruction CHECK (length(btrim(instruction)) > 0),
    CONSTRAINT ck_resolution_not_own_parent CHECK (parent_resolution_id IS NULL OR parent_resolution_id <> id)
);
CREATE INDEX ix_resolution_task ON resolution (task_id);
CREATE INDEX ix_resolution_parent ON resolution (parent_resolution_id);
CREATE INDEX ix_resolution_executor ON resolution (executor_id, state);
CREATE INDEX ix_resolution_author ON resolution (author_id, state);
CREATE INDEX ix_resolution_due_date ON resolution (due_date);

-- Co-executors (identity users), one row per user.
CREATE TABLE resolution_co_executor (
    resolution_id UUID NOT NULL REFERENCES resolution (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (resolution_id, user_id)
);
CREATE INDEX ix_resolution_co_executor_user ON resolution_co_executor (user_id);

-- Append-only history of state transitions, in order. from_state is null for the creation entry;
-- caused_by_resolution_id is set when the transition was a cascade of canceling that (ancestor) resolution.
-- actor_id has no foreign key: system operations use the nil UUID.
CREATE TABLE resolution_history (
    resolution_id           UUID        NOT NULL REFERENCES resolution (id),
    position                INTEGER     NOT NULL,
    actor_id                UUID        NOT NULL,
    from_state              VARCHAR(20),
    to_state                VARCHAR(20) NOT NULL,
    comment                 TEXT,
    occurred_at             TIMESTAMPTZ NOT NULL,
    caused_by_resolution_id UUID,
    PRIMARY KEY (resolution_id, position)
);
