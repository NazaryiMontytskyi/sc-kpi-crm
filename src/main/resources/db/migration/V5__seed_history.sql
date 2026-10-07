-- Seed framework (INC-010): one row per seeder that has been applied in this environment.
CREATE TABLE seed_history (
    seed_id    VARCHAR(150) PRIMARY KEY,
    applied_at TIMESTAMPTZ  NOT NULL
);
