package com.adient.training.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class PolyvalenceRemovalPostgresTest {
    private void database(Consumer<DriverManagerDataSource> test) {
        String schema = "remove_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://" + System.getenv("DB_HOST") + ":"
                + System.getenv("DB_PORT") + "/" + System.getenv("DB_NAME");
        var root = new JdbcTemplate(new DriverManagerDataSource(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")));
        root.execute("CREATE SCHEMA " + schema);
        try {
            var ds = new DriverManagerDataSource(url + "?currentSchema=" + schema,
                    System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
            Flyway.configure().dataSource(ds).defaultSchema(schema).target("13").load().migrate();
            test.accept(ds);
        } finally {
            root.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void seed(JdbcTemplate db) {
        db.update("INSERT INTO app_user(username,full_name,password_hash,role) VALUES " +
                "('real.employee','Real employee','unused','FORMATRICE')," +
                "('demo.pv281026.formatrice','DEMO PV28 Formatrice','unused','FORMATRICE')");
        db.update("INSERT INTO candidate(category,status,matricule,cin,full_name,created_by,remarks,created_at,updated_at) VALUES " +
                "('DIRECT','RECRUTE','REAL-001','REAL-CIN','Real employee','real','',NOW(),NOW())," +
                "('DIRECT','RECRUTE','DEMO-PV28-001','DEMO-PV28-CIN001','DEMO PV28 Salarie 1','DEMO-PV28'," +
                "'DONNEES FICTIVES DEMO PV28 - aucun salarie reel',NOW(),NOW())");
        db.update("INSERT INTO polyvalence_line(name) VALUES ('DEMO line')");
        db.update("INSERT INTO polyvalence_station(line_id,name) SELECT id,'DEMO station' FROM polyvalence_line");
        db.update("INSERT INTO polyvalence_skill(candidate_id,station_id,proposed_level,stage) " +
                "SELECT c.id,s.id,'AUTONOME','TEAM_LEADER' FROM candidate c CROSS JOIN polyvalence_station s " +
                "WHERE c.matricule='DEMO-PV28-001'");
        db.update("INSERT INTO polyvalence_event(skill_id,action,level,actor_name) " +
                "SELECT id,'PROPOSE','AUTONOME','DEMO' FROM polyvalence_skill");
    }

    @Test void upgradeRemovesModuleAndOnlyExactDemoRecordsPreservingTrainingForms() {
        database(ds -> {
            var db = new JdbcTemplate(ds);
            seed(db);
            Integer realForm = db.queryForObject("SELECT id FROM training_sheet WHERE candidate_id=" +
                    "(SELECT id FROM candidate WHERE matricule='REAL-001')", Integer.class);
            Flyway.configure().dataSource(ds).load().migrate();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM candidate", Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM training_sheet WHERE id=?", Integer.class, realForm)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() " +
                    "AND table_name LIKE 'polyvalence_%'", Integer.class)).isZero();
        });
    }

    @Test void reusedDemoAccountAbortsMigrationWithoutDetachingRealEmployee() {
        database(ds -> {
            var db = new JdbcTemplate(ds);
            seed(db);
            db.update("UPDATE candidate SET trainer_id=(SELECT id FROM app_user WHERE username='demo.pv281026.formatrice') " +
                    "WHERE matricule='REAL-001'");
            assertThatThrownBy(() -> Flyway.configure().dataSource(ds).load().migrate()).isInstanceOf(RuntimeException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM candidate WHERE matricule='REAL-001' AND trainer_id IS NOT NULL",
                    Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM candidate", Integer.class)).isEqualTo(2);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM polyvalence_skill", Integer.class)).isEqualTo(1);
        });
    }
}
