CREATE TABLE integration_booklet (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT NOT NULL UNIQUE REFERENCES candidate(id) ON DELETE CASCADE,
    training_start_date DATE,
    training_end_date DATE,
    evaluation_outcome VARCHAR(30) CHECK (evaluation_outcome IS NULL OR evaluation_outcome IN ('PENDING', 'ACQUIRED', 'TO_REINFORCE')),
    evaluation_notes VARCHAR(3000),
    candidate_signature TEXT,
    candidate_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    candidate_signed_at TIMESTAMP,
    trainer_signed_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    trainer_signed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE integration_booklet_objective (
    id BIGSERIAL PRIMARY KEY,
    booklet_id BIGINT NOT NULL REFERENCES integration_booklet(id) ON DELETE CASCADE,
    objective_key VARCHAR(40) NOT NULL,
    assessment VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (assessment IN ('PENDING', 'ACQUIRED', 'TO_REINFORCE')),
    comment VARCHAR(1000),
    UNIQUE (booklet_id, objective_key)
);

INSERT INTO integration_booklet (candidate_id, created_at, updated_at)
SELECT id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM candidate;

INSERT INTO integration_booklet_objective (booklet_id, objective_key)
SELECT b.id, o.objective_key
FROM integration_booklet b
CROSS JOIN (VALUES
    ('company'),
    ('safety'),
    ('workstation'),
    ('quality'),
    ('operations'),
    ('autonomy')
) AS o(objective_key);

CREATE FUNCTION create_candidate_integration_booklet() RETURNS TRIGGER AS $$
DECLARE
    new_booklet_id BIGINT;
BEGIN
    INSERT INTO integration_booklet (candidate_id)
    VALUES (NEW.id)
    ON CONFLICT (candidate_id) DO NOTHING
    RETURNING id INTO new_booklet_id;

    IF new_booklet_id IS NOT NULL THEN
        INSERT INTO integration_booklet_objective (booklet_id, objective_key)
        VALUES
            (new_booklet_id, 'company'),
            (new_booklet_id, 'safety'),
            (new_booklet_id, 'workstation'),
            (new_booklet_id, 'quality'),
            (new_booklet_id, 'operations'),
            (new_booklet_id, 'autonomy');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER candidate_create_integration_booklet
AFTER INSERT ON candidate
FOR EACH ROW EXECUTE FUNCTION create_candidate_integration_booklet();
