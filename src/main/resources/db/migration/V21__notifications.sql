-- Notifications: one row per recipient, delivered in-app.
-- The payload is locale-independent; texts are rendered from i18n keys at read time.
CREATE TABLE notification (
    id           UUID         PRIMARY KEY,
    recipient_id UUID         NOT NULL REFERENCES users (id),
    type         VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NOT NULL,
    target_id    UUID         NOT NULL,
    payload      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   UUID
);
CREATE INDEX ix_notification_recipient ON notification (recipient_id, created_at DESC, id);
CREATE INDEX ix_notification_unread ON notification (recipient_id) WHERE read_at IS NULL;
