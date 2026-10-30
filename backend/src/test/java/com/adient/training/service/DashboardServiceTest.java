package com.adient.training.service;

import com.adient.training.service.DashboardService.Bucket;
import com.adient.training.service.DashboardService.HrRow;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {
    private static DashboardService.DossierRow dossier(long id, String category, String position, String line, String hire) {
        return new DashboardService.DossierRow(id, "M" + id, "Name " + id, category, position, line,
                hire == null ? null : java.time.LocalDate.parse(hire), id % 2 == 0 ? "EQUIPE_A" : null, null);
    }

    @Test void groupsLinesIntoProjects() {
        var bases = new java.util.HashSet<String>();
        for (String l : List.of("P6 AV", "P6 AR", "Skoda ", "Skoda 336", "linge renfort  Skoda", "965", "CR3 AR"))
            bases.add(DashboardService.lineBase(l));
        assertThat(DashboardService.project("P6 AR", bases)).isEqualTo("P6");
        assertThat(DashboardService.project("Skoda 336", bases)).isEqualTo("SKODA");
        assertThat(DashboardService.project("linge renfort  Skoda", bases)).isEqualTo("SKODA");
        assertThat(DashboardService.project("965", bases)).isEqualTo("965");
        assertThat(DashboardService.project(" ", bases)).isEqualTo("NON_RENSEIGNE");
    }

    @Test void countsDirectPositionsProjectsAndRecruitsOfTheMonth() {
        var rows = List.of(
                dossier(1, "DIRECT", "Couturière", "P6 AV", "2026-10-03"),
                dossier(2, "DIRECT", "COUTURIERE ", "P6 AR", "2025-01-01"),
                dossier(3, "DIRECT", "Couturière", "Skoda 336", null),
                dossier(4, "DIRECT", "prép CAMPO", "Skoda", "2026-10-20"),
                dossier(5, "DIRECT", null, null, null),
                dossier(6, "FORMATION", "Couturière", null, "2026-10-21"),
                dossier(7, "INDIRECT", "Magasinier", "P6 AV", "2026-09-30"));
        var w = DashboardService.workforce(rows, YearMonth.of(2026, 10));
        assertThat(w.direct()).isEqualTo(5);
        assertThat(w.seamstresses()).isEqualTo(3);
        assertThat(w.positions()).containsExactly(new Bucket("Couturière", 3), new Bucket("prép CAMPO", 1), new Bucket("NON_RENSEIGNE", 1));
        assertThat(w.projects()).extracting(DashboardService.Project::project).containsExactly("P6", "SKODA", "NON_RENSEIGNE");
        var p6 = w.projects().get(0);
        assertThat(p6.count()).isEqualTo(2);
        assertThat(p6.seamstresses()).isEqualTo(2);
        assertThat(p6.lines()).containsExactly(new Bucket("P6 AR", 1), new Bucket("P6 AV", 1));
        assertThat(w.projects().get(1).positions()).containsExactly(new Bucket("Couturière", 1), new Bucket("prép CAMPO", 1));
        assertThat(w.month()).isEqualTo("2026-10");
        assertThat(w.recruits()).extracting(DashboardService.Recruit::candidateId).containsExactly(6L, 4L, 1L);
        assertThat(w.recruits().get(1).project()).isEqualTo("SKODA");
        assertThat(w.recruits().get(1).shiftLabel()).isEqualTo("Équipe A");
        assertThat(w.recruitsByCategory()).containsExactly(new Bucket("FORMATION", 1), new Bucket("DIRECT", 2),
                new Bucket("INDIRECT", 0), new Bucket("CADRE", 0));
    }

    private static HrRow row(boolean active, String type, String line, String gender, String age, String hire, String departure) {
        return new HrRow(active, type, " production ", line, gender, "cdi", "< 1", age, hire, departure);
    }

    @Test void aggregatesActiveEmployeesAndNormalizesValues() {
        var rows = List.of(
                row(true, "DIRECT", "Audi", "FEMME", "< 25", "2026-10-05T00:00:00", null),
                row(true, "DIRECT", "Audi", "Homme ", ">35< 91", "2026-09-01T00:00:00", null),
                row(true, "CADRE", "", "femme", "", "#VALUE!", null),
                row(false, "INDIRECT", "Audi", "FEMME", "< 25", "2025-01-01T00:00:00", "2026-10-20T00:00:00"));
        var hr = DashboardService.hr(rows, YearMonth.of(2026, 10));
        assertThat(hr.employees()).isEqualTo(4);
        assertThat(hr.active()).isEqualTo(3);
        assertThat(hr.inactive()).isEqualTo(1);
        assertThat(hr.hiresThisMonth()).isEqualTo(1);
        assertThat(hr.departuresThisMonth()).isEqualTo(1);
        assertThat(hr.byType()).containsExactly(new Bucket("CADRE", 1), new Bucket("DIRECT", 2), new Bucket("INDIRECT", 0));
        assertThat(hr.gender()).containsExactly(new Bucket("FEMME", 2), new Bucket("HOMME", 1));
        assertThat(hr.byDepartment()).containsExactly(new Bucket("PRODUCTION", 3));
        assertThat(hr.contract()).containsExactly(new Bucket("CDI", 3));
        assertThat(hr.byLine()).containsExactly(new Bucket("AUDI", 2), new Bucket(DashboardService.NONE, 1));
        assertThat(hr.age()).contains(new Bucket("< 25", 1), new Bucket(DashboardService.OTHERS, 1), new Bucket(DashboardService.NONE, 1));
        assertThat(hr.months()).hasSize(12);
        assertThat(hr.months().getFirst().month()).isEqualTo("2025-11");
        assertThat(hr.months().getLast()).isEqualTo(new DashboardService.Month("2026-10", 1, 1));
    }

    @Test void groupsSmallLinesIntoOthers() {
        List<HrRow> rows = new ArrayList<>();
        for (int i = 0; i < 14; i++) rows.add(row(true, "DIRECT", "L" + i, "FEMME", "< 25", null, null));
        var lines = DashboardService.hr(rows, YearMonth.of(2026, 10)).byLine();
        assertThat(lines).hasSize(13);
        assertThat(lines.getLast()).isEqualTo(new Bucket(DashboardService.OTHERS, 2));
    }
}
