-- Task assignment model (ADR-0010, TASK_TRACKING_FEATURE.md 5.1-5.5): deadline, issue date, creator, one-level
-- subtasks, optimistic locking; assignees, artifacts and reminder idempotency. Nothing is deleted.

-- Deadline: the former due date, values preserved by the rename (a date due at the end of that day, Europe/Kyiv).
ALTER TABLE task RENAME COLUMN due_date TO deadline;
ALTER INDEX ix_task_due_date RENAME TO ix_task_deadline;

ALTER TABLE task
    ADD COLUMN creator_id     UUID,
    ADD COLUMN issued_at      TIMESTAMPTZ,
    ADD COLUMN parent_task_id UUID,
    ADD COLUMN version        BIGINT NOT NULL DEFAULT 0;

-- Backfill: the creator is the creating user (the nil system actor if unknown), the issue date the creation time.
UPDATE task
   SET creator_id = COALESCE(created_by, '00000000-0000-0000-0000-000000000000'),
       issued_at  = created_at;

ALTER TABLE task
    ALTER COLUMN creator_id SET NOT NULL,
    ALTER COLUMN issued_at SET NOT NULL,
    ADD CONSTRAINT fk_task_parent FOREIGN KEY (parent_task_id) REFERENCES task (id),
    ADD CONSTRAINT ck_task_not_own_parent CHECK (parent_task_id IS NULL OR parent_task_id <> id);
CREATE INDEX ix_task_parent ON task (parent_task_id);
CREATE INDEX ix_task_creator ON task (creator_id);

-- Assignees: a separate row per user so a per-assignee state can be added later (ADR-0010). Unassigning sets
-- removed_at; the row stays. A user is an active assignee of a task at most once.
CREATE TABLE task_assignee (
    id          UUID        PRIMARY KEY,
    task_id     UUID        NOT NULL REFERENCES task (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    assigned_by UUID        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    removed_at  TIMESTAMPTZ,
    removed_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID,
    CONSTRAINT ck_task_assignee_removed CHECK (removed_by IS NULL OR removed_at IS NOT NULL)
);
CREATE UNIQUE INDEX uq_task_assignee_active ON task_assignee (task_id, user_id) WHERE removed_at IS NULL;
CREATE INDEX ix_task_assignee_task ON task_assignee (task_id);
CREATE INDEX ix_task_assignee_user ON task_assignee (user_id, task_id);

-- Artifacts: an uploaded file (files module) or an external http(s) link. Removal archives the row.
CREATE TABLE task_artifact (
    id            UUID          PRIMARY KEY,
    task_id       UUID          NOT NULL REFERENCES task (id),
    artifact_type VARCHAR(10)   NOT NULL,
    file_id       UUID          REFERENCES file_object (id),
    url           VARCHAR(2048),
    title         VARCHAR(300),
    added_by      UUID          NOT NULL,
    added_at      TIMESTAMPTZ   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_at    TIMESTAMPTZ   NOT NULL,
    updated_by    UUID,
    archived_at   TIMESTAMPTZ,
    archived_by   UUID,
    CONSTRAINT ck_task_artifact_type CHECK (artifact_type IN ('FILE', 'LINK')),
    CONSTRAINT ck_task_artifact_target CHECK (
        (artifact_type = 'FILE' AND file_id IS NOT NULL AND url IS NULL)
        OR (artifact_type = 'LINK' AND url IS NOT NULL AND file_id IS NULL)),
    CONSTRAINT ck_task_artifact_url_scheme CHECK (url IS NULL OR url ~* '^https?://')
);
CREATE INDEX ix_task_artifact_task ON task_artifact (task_id);
CREATE INDEX ix_task_artifact_file ON task_artifact (file_id);

-- Idempotency of deadline reminders: at most one notification per task, recipient, threshold and deadline. Keyed by
-- deadline so a moved deadline re-arms the reminders. Successor of resolution_reminder (kept read-only, ADR-0010).
CREATE TABLE task_reminder (
    task_id   UUID        NOT NULL REFERENCES task (id),
    user_id   UUID        NOT NULL REFERENCES users (id),
    threshold VARCHAR(32) NOT NULL,
    deadline  DATE        NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (task_id, user_id, threshold, deadline)
);
CREATE INDEX ix_task_reminder_user ON task_reminder (user_id);
