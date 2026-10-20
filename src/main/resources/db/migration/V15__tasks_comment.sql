-- Comments: generic over the commented record (TASK now, RESOLUTION later), so there is no foreign key to the target.
-- The body is a rich-text document (TipTap JSON). Comments are archived (soft delete), never deleted.
CREATE TABLE comment (
    id          UUID        PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id   UUID        NOT NULL,
    body        JSONB       NOT NULL,
    edited_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_comment_target_type CHECK (target_type IN ('TASK', 'RESOLUTION'))
);
CREATE INDEX ix_comment_target ON comment (target_type, target_id, created_at);
CREATE INDEX ix_comment_created_by ON comment (created_by);

-- Mentioned users (ids of identity users), one row per user.
CREATE TABLE comment_mention (
    comment_id UUID NOT NULL REFERENCES comment (id),
    user_id    UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (comment_id, user_id)
);
CREATE INDEX ix_comment_mention_user ON comment_mention (user_id);

-- Attachments are files of the files module, in display order.
CREATE TABLE comment_attachment (
    comment_id UUID    NOT NULL REFERENCES comment (id),
    position   INTEGER NOT NULL,
    file_id    UUID    NOT NULL REFERENCES file_object (id),
    PRIMARY KEY (comment_id, position)
);
