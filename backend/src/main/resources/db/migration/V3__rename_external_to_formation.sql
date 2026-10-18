ALTER TABLE candidate DROP CONSTRAINT candidate_category_check;
UPDATE candidate SET category = 'FORMATION' WHERE category = 'EXTERNAL';
ALTER TABLE candidate ADD CONSTRAINT candidate_category_check
    CHECK (category IN ('CADRE', 'DIRECT', 'INDIRECT', 'FORMATION'));