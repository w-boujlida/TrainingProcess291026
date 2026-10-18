ALTER TABLE candidate ADD COLUMN training_level VARCHAR(20)
    CHECK (training_level IN ('APPRENTIE', 'JUNIOR', 'CONFIRMEE'));