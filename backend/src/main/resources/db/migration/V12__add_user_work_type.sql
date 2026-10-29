ALTER TABLE app_user ADD COLUMN work_type VARCHAR(10)
    CHECK (work_type IN ('CADRE', 'DIRECT', 'INDIRECT'));
