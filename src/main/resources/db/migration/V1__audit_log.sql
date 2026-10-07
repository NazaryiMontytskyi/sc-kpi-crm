-- Append-only audit log (CONTEXT.md 5.10). Rows are never updated or deleted.
CREATE TABLE audit_log (
    id           UUID         PRIMARY KEY,
    actor_id     UUID,
    action       VARCHAR(64)  NOT NULL,
    entity_type  VARCHAR(128) NOT NULL,
    entity_id    UUID,
    before_state JSONB,
    after_state  JSONB,
    at           TIMESTAMPTZ  NOT NULL,
    ip           VARCHAR(45)
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id, at DESC);
CREATE INDEX idx_audit_log_actor  ON audit_log (actor_id, at DESC);
CREATE INDEX idx_audit_log_at     ON audit_log (at DESC);

CREATE FUNCTION audit_log_forbid_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is forbidden', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_forbid_modification();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_forbid_modification();
