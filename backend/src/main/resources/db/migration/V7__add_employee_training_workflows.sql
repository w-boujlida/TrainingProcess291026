ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'SUPERVISEUR', 'FORMATRICE', 'TEAM_LEADER', 'QUALITY'));

CREATE TABLE training_sheet (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL UNIQUE REFERENCES candidate(id) ON DELETE CASCADE,
    supervisor_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    supervisor_signed_at TIMESTAMP,
    quality_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    quality_signed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE training_period (
    id BIGSERIAL PRIMARY KEY,
    sheet_id BIGINT NOT NULL REFERENCES training_sheet(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    sequence_no INTEGER NOT NULL,
    start_date DATE,
    end_date DATE,
    trainer_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    trainer_signed_at TIMESTAMP,
    team_leader_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    team_leader_signed_at TIMESTAMP,
    UNIQUE (sheet_id, sequence_no)
);

CREATE TABLE training_competency (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES training_period(id) ON DELETE CASCADE,
    label VARCHAR(300) NOT NULL,
    sequence_no INTEGER NOT NULL,
    rating INTEGER CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    comment VARCHAR(1000),
    UNIQUE (period_id, sequence_no)
);

CREATE FUNCTION seed_training_sheet(p_candidate_id BIGINT) RETURNS VOID AS $$
DECLARE
    new_sheet_id BIGINT;
    period_row RECORD;
BEGIN
    INSERT INTO training_sheet (candidate_id)
    VALUES (p_candidate_id)
    ON CONFLICT (candidate_id) DO NOTHING
    RETURNING id INTO new_sheet_id;

    IF new_sheet_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO training_period (sheet_id, title, sequence_no) VALUES
        (new_sheet_id, 'Préparation', 1),
        (new_sheet_id, 'Connaissance', 2),
        (new_sheet_id, 'Application', 3),
        (new_sheet_id, 'Autonomie', 4),
        (new_sheet_id, 'Contrôle final', 5);

    FOR period_row IN SELECT id, sequence_no FROM training_period WHERE sheet_id = new_sheet_id LOOP
        CASE period_row.sequence_no
            WHEN 1 THEN
                INSERT INTO training_competency (period_id, label, sequence_no) VALUES
                    (period_row.id, 'Présentation de l’entreprise et des règles d’intégration', 1),
                    (period_row.id, 'Présentation du poste et de l’espace de travail', 2),
                    (period_row.id, 'Connaissance des EPI et des règles de sécurité', 3);
            WHEN 2 THEN
                INSERT INTO training_competency (period_id, label, sequence_no) VALUES
                    (period_row.id, 'Identifier les outils, équipements et composants', 1),
                    (period_row.id, 'Identifier les étapes et consignes du processus', 2),
                    (period_row.id, 'Connaître les critères qualité du poste', 3);
            WHEN 3 THEN
                INSERT INTO training_competency (period_id, label, sequence_no) VALUES
                    (period_row.id, 'Appliquer le mode opératoire avec accompagnement', 1),
                    (period_row.id, 'Réaliser les opérations en respectant les consignes', 2),
                    (period_row.id, 'Appliquer les règles de qualité et de sécurité', 3);
            WHEN 4 THEN
                INSERT INTO training_competency (period_id, label, sequence_no) VALUES
                    (period_row.id, 'Réaliser les opérations de manière autonome', 1),
                    (period_row.id, 'Respecter les objectifs de cadence et de qualité', 2),
                    (period_row.id, 'Détecter et signaler les anomalies', 3);
            WHEN 5 THEN
                INSERT INTO training_competency (period_id, label, sequence_no) VALUES
                    (period_row.id, 'Maîtriser les opérations principales du poste', 1),
                    (period_row.id, 'Respecter les exigences qualité et sécurité', 2),
                    (period_row.id, 'Définir les besoins de formation complémentaire', 3);
        END CASE;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT seed_training_sheet(id) FROM candidate;

CREATE FUNCTION create_candidate_training_sheet() RETURNS TRIGGER AS $$
BEGIN
    PERFORM seed_training_sheet(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER candidate_create_training_sheet
AFTER INSERT ON candidate
FOR EACH ROW EXECUTE FUNCTION create_candidate_training_sheet();
