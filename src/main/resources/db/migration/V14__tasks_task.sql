-- Tasks: a task lives in a space and has one of the space's statuses. No hierarchy: tasks can only be linked to each
-- other by symmetric "related" references. CONTEXT.md 5.3. Tasks are archived (soft delete), never deleted.
CREATE TABLE task (
    id                  UUID         PRIMARY KEY,
    space_id            UUID         NOT NULL REFERENCES task_space (id),
    title               VARCHAR(300) NOT NULL,
    description         JSONB,
    status_id           UUID         NOT NULL REFERENCES task_space_status (id),
    priority            VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    due_date            DATE,
    custom_field_values JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          UUID,
    archived_at         TIMESTAMPTZ,
    archived_by         UUID,
    CONSTRAINT ck_task_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_task_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);
CREATE INDEX ix_task_space ON task (space_id);
CREATE INDEX ix_task_status ON task (status_id);
CREATE INDEX ix_task_due_date ON task (due_date);
CREATE INDEX ix_task_created_by ON task (created_by);

-- Labels are free-form, stored lower-cased.
CREATE TABLE task_label (
    task_id UUID        NOT NULL REFERENCES task (id),
    label   VARCHAR(50) NOT NULL,
    PRIMARY KEY (task_id, label)
);
CREATE INDEX ix_task_label_label ON task_label (label);

-- Attachments are files of the files module, in display order.
CREATE TABLE task_attachment (
    task_id  UUID    NOT NULL REFERENCES task (id),
    position INTEGER NOT NULL,
    file_id  UUID    NOT NULL REFERENCES file_object (id),
    PRIMARY KEY (task_id, position)
);

-- A related link is symmetric: stored once, the lower id first.
CREATE TABLE task_link (
    task_low  UUID NOT NULL REFERENCES task (id),
    task_high UUID NOT NULL REFERENCES task (id),
    PRIMARY KEY (task_low, task_high),
    CONSTRAINT ck_task_link_order CHECK (task_low < task_high)
);
CREATE INDEX ix_task_link_high ON task_link (task_high);
