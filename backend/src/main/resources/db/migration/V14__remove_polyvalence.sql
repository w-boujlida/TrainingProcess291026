-- Keep V13 unchanged for databases where it has already been applied.
DROP TABLE polyvalence_event;
DROP TABLE polyvalence_skill;
DROP TABLE polyvalence_station;
DROP TABLE polyvalence_line;

-- Only the four fictitious employees created for this simulation are removed.
DELETE FROM candidate
WHERE created_by = 'DEMO-PV28'
  AND remarks = 'DONNEES FICTIVES DEMO PV28 - aucun salarie reel'
  AND (matricule, cin, full_name) IN (
    ('DEMO-PV28-001', 'DEMO-PV28-CIN001', 'DEMO PV28 Salarie 1'),
    ('DEMO-PV28-002', 'DEMO-PV28-CIN002', 'DEMO PV28 Salarie 2'),
    ('DEMO-PV28-003', 'DEMO-PV28-CIN003', 'DEMO PV28 Salarie 3'),
    ('DEMO-PV28-004', 'DEMO-PV28-CIN004', 'DEMO PV28 Salarie 4')
  );

-- Do not detach a real employee or an existing training signature from a demo
-- account that might have been reused since the simulation.
DO $$
DECLARE
    demo_id BIGINT;
    fk RECORD;
    used BOOLEAN;
BEGIN
    FOR demo_id IN
        SELECT id FROM app_user WHERE (username, full_name, role) IN (
            ('demo.pv281026.admin', 'DEMO PV28 Administration', 'ADMIN'),
            ('demo.pv281026.formatrice', 'DEMO PV28 Formatrice', 'FORMATRICE'),
            ('demo.pv281026.teamleader', 'DEMO PV28 Team Leader', 'TEAM_LEADER'),
            ('demo.pv281026.superviseur', 'DEMO PV28 Superviseur', 'SUPERVISEUR'),
            ('demo.pv281026.qualite', 'DEMO PV28 Qualite', 'QUALITY')
        )
    LOOP
        FOR fk IN
            SELECT ns.nspname AS schema_name, rel.relname AS table_name, att.attname AS column_name
            FROM pg_constraint c
            JOIN pg_class rel ON rel.oid = c.conrelid
            JOIN pg_namespace ns ON ns.oid = rel.relnamespace
            JOIN pg_attribute att ON att.attrelid = c.conrelid AND att.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND c.confrelid = 'app_user'::regclass
        LOOP
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I.%I WHERE %I=$1)',
                fk.schema_name, fk.table_name, fk.column_name) INTO used USING demo_id;
            IF used THEN
                RAISE EXCEPTION 'A DEMO account is referenced outside the removed simulation. Cleanup aborted to preserve existing data.';
            END IF;
        END LOOP;
        DELETE FROM app_user WHERE id = demo_id;
    END LOOP;
END $$;
