-- Idempotency of the due-date reminders (INC-044): at most one notification per resolution, threshold and due date.
-- Keyed by due date too, so a resolution whose due date is moved gets its reminders again.
CREATE TABLE resolution_reminder (
    resolution_id UUID         NOT NULL REFERENCES resolution (id) ON DELETE CASCADE,
    threshold     VARCHAR(32)  NOT NULL,
    due_date      DATE         NOT NULL,
    sent_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (resolution_id, threshold, due_date)
);
