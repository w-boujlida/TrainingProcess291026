CREATE TABLE polyvalence_line (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT polyvalence_line_name_unique UNIQUE (name)
);
CREATE UNIQUE INDEX polyvalence_line_name_lower ON polyvalence_line (lower(name));

CREATE TABLE polyvalence_station (
    id BIGSERIAL PRIMARY KEY,
    line_id BIGINT NOT NULL REFERENCES polyvalence_line(id),
    name VARCHAR(120) NOT NULL,
    UNIQUE (line_id, name)
);
CREATE UNIQUE INDEX polyvalence_station_name_lower ON polyvalence_station (line_id, lower(name));

CREATE TABLE polyvalence_skill (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL REFERENCES candidate(id) ON DELETE CASCADE,
    station_id BIGINT NOT NULL REFERENCES polyvalence_station(id),
    confirmed_level VARCHAR(20) NOT NULL DEFAULT 'NON_FORME'
        CHECK (confirmed_level IN ('NON_FORME','EN_FORMATION','AUTONOME','FORMATEUR')),
    proposed_level VARCHAR(20) NOT NULL
        CHECK (proposed_level IN ('NON_FORME','EN_FORMATION','AUTONOME','FORMATEUR')),
    stage VARCHAR(30) NOT NULL CHECK (stage IN ('TEAM_LEADER','SUPERVISEUR','VALIDATED','REJECTED')),
    comment VARCHAR(1000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (candidate_id, station_id)
);

CREATE TABLE polyvalence_event (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES polyvalence_skill(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    level VARCHAR(20) NOT NULL,
    actor_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    actor_name VARCHAR(120) NOT NULL,
    comment VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX polyvalence_event_skill ON polyvalence_event(skill_id, id);
