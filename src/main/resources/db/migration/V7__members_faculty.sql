-- Members: managed faculty/institute dictionary (CONTEXT.md 5.1, decision Q5). Rows are archived, never deleted.
-- Code and both names must be unique among ACTIVE faculties, case-insensitively.
CREATE TABLE faculty (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name_uk     VARCHAR(200) NOT NULL,
    name_en     VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    CONSTRAINT ck_faculty_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_faculty_name_uk CHECK (length(btrim(name_uk)) > 0),
    CONSTRAINT ck_faculty_name_en CHECK (length(btrim(name_en)) > 0)
);
CREATE UNIQUE INDEX uq_faculty_code_active ON faculty (lower(code)) WHERE archived_at IS NULL;
CREATE UNIQUE INDEX uq_faculty_name_uk_active ON faculty (lower(name_uk)) WHERE archived_at IS NULL;
CREATE UNIQUE INDEX uq_faculty_name_en_active ON faculty (lower(name_en)) WHERE archived_at IS NULL;
