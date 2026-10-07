-- Identity: user accounts (CONTEXT.md 5.1, 6). Passwords are stored only as BCrypt hashes.
CREATE TABLE users (
    id                   UUID         PRIMARY KEY,
    login                VARCHAR(128) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    locale               VARCHAR(2)   NOT NULL DEFAULT 'uk',
    last_login_at        TIMESTAMPTZ,
    must_change_password BOOLEAN      NOT NULL,
    password_changed_at  TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           UUID,
    archived_at          TIMESTAMPTZ,
    archived_by          UUID,
    CONSTRAINT uq_users_login UNIQUE (login),
    CONSTRAINT ck_users_login_normalized CHECK (login = lower(btrim(login)) AND length(login) > 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'BLOCKED')),
    CONSTRAINT ck_users_locale CHECK (locale IN ('uk', 'en'))
);
