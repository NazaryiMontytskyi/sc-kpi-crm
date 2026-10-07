-- Files: metadata of uploaded files (CONTEXT.md 5.8/5.9); bytes live in the configured FileStorage.
CREATE TABLE file_object (
    id          UUID         PRIMARY KEY,
    storage_key VARCHAR(100) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(150) NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    uploaded_by UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT uq_file_object_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_file_object_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_file_object_uploaded_by ON file_object (uploaded_by);
