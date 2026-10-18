CREATE TABLE candidate (
    id               BIGSERIAL PRIMARY KEY,
    category         VARCHAR(20)  NOT NULL CHECK (category IN ('CADRE', 'DIRECT', 'INDIRECT', 'EXTERNAL')),
    status           VARCHAR(20)  NOT NULL DEFAULT 'CANDIDAT'
                     CHECK (status IN ('CANDIDAT', 'RECRUTE', 'EN_FORMATION', 'VALIDE', 'REJETE')),
    matricule        VARCHAR(30),
    full_name        VARCHAR(120) NOT NULL,
    cin              VARCHAR(20)  NOT NULL,
    birth_date       DATE,
    phone            VARCHAR(30),
    email            VARCHAR(150),
    address          VARCHAR(255),
    position         VARCHAR(120),
    department       VARCHAR(120),
    hire_date        DATE,
    education_level  VARCHAR(120),
    shift            VARCHAR(20)  CHECK (shift IN ('MATIN', 'APRES_MIDI', 'NUIT', 'NORMAL')),
    trainer_id       BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    team_leader_id   BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    remarks          VARCHAR(2000),
    created_by       VARCHAR(50),
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX ux_candidate_cin ON candidate (upper(cin));
CREATE UNIQUE INDEX ux_candidate_matricule ON candidate (upper(matricule)) WHERE matricule IS NOT NULL;
CREATE INDEX ix_candidate_category ON candidate (category);
