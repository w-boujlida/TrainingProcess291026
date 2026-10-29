CREATE TABLE workstation_form (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL UNIQUE REFERENCES candidate(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workstation_cycle (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES workstation_form(id) ON DELETE CASCADE,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('INITIAL', 'REFRESHER')),
    start_date DATE,
    end_date DATE,
    evaluation_date DATE,
    measures VARCHAR(3000),
    operator_signature TEXT,
    operator_signed_at TIMESTAMP,
    operator_recorded_by VARCHAR(120),
    UNIQUE (form_id, kind)
);

CREATE TABLE workstation_step (
    id BIGSERIAL PRIMARY KEY,
    cycle_id BIGINT NOT NULL REFERENCES workstation_cycle(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    label VARCHAR(500) NOT NULL,
    applicability VARCHAR(10) NOT NULL,
    concerned_positions VARCHAR(100) NOT NULL,
    deadline DATE,
    training_status VARCHAR(10) CHECK (training_status IN ('X', 'NA')),
    evaluation_status VARCHAR(10) CHECK (evaluation_status IN ('OK', 'NOK')),
    signed_by VARCHAR(120),
    signed_at TIMESTAMP,
    UNIQUE (cycle_id, sequence_no),
    CHECK (training_status IS DISTINCT FROM 'NA' OR evaluation_status IS NULL)
);

CREATE FUNCTION seed_workstation_form(p_candidate_id BIGINT) RETURNS VOID AS $$
DECLARE new_form_id BIGINT;
BEGIN
    INSERT INTO workstation_form(candidate_id) VALUES (p_candidate_id)
    ON CONFLICT (candidate_id) DO NOTHING RETURNING id INTO new_form_id;
    IF new_form_id IS NULL THEN RETURN; END IF;

    INSERT INTO workstation_cycle(form_id, kind)
    VALUES (new_form_id, 'INITIAL'), (new_form_id, 'REFRESHER');

    INSERT INTO workstation_step(cycle_id, sequence_no, label, applicability, concerned_positions)
    SELECT cycle.id, step.sequence_no, step.label, step.applicability, step.positions
    FROM workstation_cycle cycle
    CROSS JOIN (VALUES
        (1, '1. Présentation générale de l’entreprise', 'I', 'Tous'),
        (2, '2. Sécurité — Signes et sigles de sécurité / identification des produits', 'I+R+E', 'Tous'),
        (3, '2. Sécurité — Les EPI / L’hygiène dans le travail', 'I+R+E', 'Tous'),
        (4, '2. Sécurité — Gestes et postures et règles de manutention', 'I+R+E', 'Tous'),
        (5, '2. Sécurité — Règles de circulation interne (flux, traçage, plan d’évacuation et issues de secours)', 'I+R+E', 'Tous'),
        (6, '3. Formation règlement intérieur « Code of conduct »', 'I', 'Tous'),
        (7, '4. Formation Politique Qualité', 'I', 'Tous'),
        (8, '5. Formation Politique anti-corruption, Politique d’escalation', 'I', 'Tous'),
        (9, '6. Présentation du projet, produit et client', 'I', 'Tous'),
        (10, '7. Formation sur les exigences réglementaires produit / client (SC / CC au plan client)', 'I+R+E', 'Tous'),
        (11, '8. Formation sur les exigences clients CSR', 'I+R+E', 'Tous'),
        (12, '9. Formation sur le processus de fabrication', 'I', 'Opérateurs'),
        (13, '10. Formation 5S poste (rangement, nettoyage, matériels)', 'I+R+E', 'Tous'),
        (14, '11. Présentation et formation sur les chemins de contrôle', 'I+R+E', 'Contrôleurs'),
        (15, '12. Présentation des défauts produits connus', 'I+R+E', 'Contrôleurs + opérateurs'),
        (16, '13. Formation sur les coiffes types et leur mode d’utilisation', 'I+E', 'Contrôleurs + opérateurs'),
        (17, '14. Formation sur les paramètres de couture du projet', 'I+R+E', 'Contrôleurs + opérateurs'),
        (18, '15. Formation sur les instructions de travail', 'I+R+E', 'Tous'),
        (19, '16. Formation sur la maintenance premier niveau', 'I+R+E', 'Opérateurs'),
        (20, '17. Formation sur l’OK démarrage / OK première pièce', 'I+R+E', 'Contrôleurs + opérateurs'),
        (21, '18. Formation sur l’instruction de l’emballage', 'I+R+E', 'Contrôleurs'),
        (22, '19. Présentation des méthodes de livraison, de stockage et de gerbage', 'I+R+E', 'Magasinier'),
        (23, '20. Présentation du layout de l’atelier de production (flux de produit et méthodes d’alimentation des lignes)', 'I+R+E', 'Magasinier'),
        (24, '21. Présentation de la méthode FIFO (tableau de gestion visuel du FIFO)', 'I+R+E', 'Magasinier'),
        (25, '22. Déclaration de la production en temps réel (douchage)', 'I+R+E', 'Magasinier'),
        (26, '23. Formation sur la conduite des chariots élévateurs et ses circuits', 'I+R+E', 'Magasinier')
    ) AS step(sequence_no, label, applicability, positions)
    WHERE cycle.form_id = new_form_id;
END;
$$ LANGUAGE plpgsql;

SELECT seed_workstation_form(id) FROM candidate;

CREATE FUNCTION create_candidate_workstation_form() RETURNS TRIGGER AS $$
BEGIN
    PERFORM seed_workstation_form(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER candidate_create_workstation_form AFTER INSERT ON candidate
FOR EACH ROW EXECUTE FUNCTION create_candidate_workstation_form();
