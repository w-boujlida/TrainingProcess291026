package db.migration;

import com.adient.training.service.RecruitmentHrSync;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** One-time placement of the already imported HR employees into Cadre / Direct / Indirect. */
public class V16__sync_recruitment_from_hr extends BaseJavaMigration {
    @Override
    public void migrate(Context context) {
        JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        RecruitmentHrSync.prepareSchema(db);
        new RecruitmentHrSync(db).sync();
    }
}
