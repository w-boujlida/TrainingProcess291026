CREATE TABLE app_user (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    full_name             VARCHAR(120) NOT NULL,
    email                 VARCHAR(150),
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(30)  NOT NULL
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'SUPERVISEUR', 'FORMATRICE', 'TEAM_LEADER')),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_app_user_username_lower ON app_user (LOWER(username));
CREATE INDEX ix_app_user_role ON app_user (role);
