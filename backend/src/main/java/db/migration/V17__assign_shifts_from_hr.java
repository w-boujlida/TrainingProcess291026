package db.migration;

import com.adient.training.service.RecruitmentHrSync;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Adds the HR teams A / B as shifts and links Cadre / Direct / Indirect dossiers to their shift and team leader. */
public class V17__assign_shifts_from_hr extends BaseJavaMigration {
    @Override
    public void migrate(Context context) {
        JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        RecruitmentHrSync.prepareSchema(db);
        new RecruitmentHrSync(db).sync();
    }
}
