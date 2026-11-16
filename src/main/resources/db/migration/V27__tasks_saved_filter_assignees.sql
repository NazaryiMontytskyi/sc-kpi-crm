-- Saved task filters follow the task list filters of the assignment model (TASK_TRACKING_FEATURE.md 3, 6.3):
-- assignee, creator, overdue and deadline range; the quick view "My resolutions" becomes "My tasks". The legacy
-- resolution criteria (executor_id, author_id, resolution_state) stay until resolutions are removed.
ALTER TABLE task_saved_filter RENAME COLUMN due_from TO deadline_from;
ALTER TABLE task_saved_filter RENAME COLUMN due_to TO deadline_to;

ALTER TABLE task_saved_filter
    ADD COLUMN assignee_id UUID,
    ADD COLUMN creator_id  UUID,
    ADD COLUMN overdue     BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task_saved_filter DROP CONSTRAINT ck_task_saved_filter_view;
UPDATE task_saved_filter SET quick_view = 'MY_TASKS' WHERE quick_view = 'MY_RESOLUTIONS';
ALTER TABLE task_saved_filter ADD CONSTRAINT ck_task_saved_filter_view CHECK (quick_view IS NULL OR quick_view IN
    ('MY_TASKS', 'ISSUED_BY_ME', 'OVERDUE'));
