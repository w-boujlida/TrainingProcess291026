ALTER TABLE training_sheet
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN behavior_opinion VARCHAR(1000),
    ADD COLUMN knowledge_opinion VARCHAR(1000),
    ADD COLUMN machine_opinion VARCHAR(1000),
    ADD COLUMN sewing_opinion VARCHAR(1000);

ALTER TABLE training_competency
    ADD COLUMN section VARCHAR(20) NOT NULL DEFAULT 'BEHAVIOR'
        CHECK (section IN ('BEHAVIOR', 'KNOWLEDGE', 'MACHINE', 'SEWING'));

-- The replacement of all prior evaluations was explicitly requested.
DELETE FROM training_period;
UPDATE training_sheet SET
    supervisor_signed_by = NULL, supervisor_signed_at = NULL,
    quality_signed_by = NULL, quality_signed_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

CREATE FUNCTION seed_sewing_training_periods(p_sheet_id BIGINT) RETURNS VOID AS $$
BEGIN
    INSERT INTO training_period (sheet_id, title, sequence_no)
    SELECT p_sheet_id, 'Semaine ' || week_no, week_no
    FROM generate_series(1, 5) AS weeks(week_no);

    INSERT INTO training_competency (period_id, section, label, sequence_no)
    SELECT period.id, criteria.section, criteria.label, criteria.sequence_no
    FROM training_period period
    CROSS JOIN (VALUES
        ('BEHAVIOR', 'Ponctualité', 1),
        ('BEHAVIOR', 'Présence', 2),
        ('BEHAVIOR', 'Engagement / Responsabilité', 3),
        ('BEHAVIOR', 'Relation avec les autres collègues', 4),
        ('BEHAVIOR', 'Propreté du poste de travail', 5),
        ('BEHAVIOR', 'Application du règlement de travail', 6),
        ('BEHAVIOR', 'Capacité d’apprendre', 7),
        ('KNOWLEDGE', 'Lignes de couture droites et parallèles, à moins de 5 mm d’espace', 8),
        ('KNOWLEDGE', 'Lignes de couture courbes', 9),
        ('KNOWLEDGE', 'Maîtrise de soi', 10),
        ('KNOWLEDGE', 'Manipulation correcte des pièces', 11),
        ('MACHINE', 'Enfilage de machines à coudre', 12),
        ('MACHINE', 'Placement des bobines de fils', 13),
        ('MACHINE', 'Reconnaissance et placement de l’aiguille', 14),
        ('MACHINE', 'Coordination mains et pieds', 15),
        ('SEWING', 'Alignement des crans', 16),
        ('SEWING', 'Aspect des lignes de couture', 17),
        ('SEWING', 'Bord à bord', 18),
        ('SEWING', 'Exécution du point arrière', 19),
        ('SEWING', 'Coudre le non-tissé sur le tissu', 20),
        ('SEWING', 'Couture poche', 21),
        ('SEWING', 'Couture de bande velcro', 22),
        ('SEWING', 'Validation de la couture de reprise', 23),
        ('SEWING', 'Exécution du rempli', 24),
        ('SEWING', 'Application de fermeture Zip', 25),
        ('SEWING', 'Couture du profil', 26)
    ) AS criteria(section, label, sequence_no)
    WHERE period.sheet_id = p_sheet_id;
END;
$$ LANGUAGE plpgsql;

SELECT seed_sewing_training_periods(id) FROM training_sheet;

CREATE OR REPLACE FUNCTION seed_training_sheet(p_candidate_id BIGINT) RETURNS VOID AS $$
DECLARE
    new_sheet_id BIGINT;
BEGIN
    INSERT INTO training_sheet (candidate_id)
    VALUES (p_candidate_id)
    ON CONFLICT (candidate_id) DO NOTHING
    RETURNING id INTO new_sheet_id;

    IF new_sheet_id IS NOT NULL THEN
        PERFORM seed_sewing_training_periods(new_sheet_id);
    END IF;
END;
$$ LANGUAGE plpgsql;
