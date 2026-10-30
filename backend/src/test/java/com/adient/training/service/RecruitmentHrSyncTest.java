package com.adient.training.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentHrSyncTest {
    @Test void cleansCinValues() {
        assertThat(RecruitmentHrSync.cin(" 1234567 ")).isEqualTo("01234567");
        assertThat(RecruitmentHrSync.cin("12345678")).isEqualTo("12345678");
        assertThat(RecruitmentHrSync.cin("AB123456789")).isEqualTo("AB123456789");
        assertThat(RecruitmentHrSync.cin("")).isNull();
        assertThat(RecruitmentHrSync.cin("#N/A")).isNull();
        assertThat(RecruitmentHrSync.cin("Non calculé dans Excel")).isNull();
        assertThat(RecruitmentHrSync.cin("123456789012345678901")).isNull();
    }

    @Test void mapsHrTeamToShift() {
        assertThat(RecruitmentHrSync.shift("A", "DIRECT", null)).isEqualTo("EQUIPE_A");
        assertThat(RecruitmentHrSync.shift(" b ", "INDIRECT", "MATIN")).isEqualTo("EQUIPE_B");
        assertThat(RecruitmentHrSync.shift("Poste A", "DIRECT", null)).isEqualTo("EQUIPE_A");
        assertThat(RecruitmentHrSync.shift("", "CADRE", null)).isEqualTo("NORMAL");
        assertThat(RecruitmentHrSync.shift(null, "CADRE", "MATIN")).isNull();
        assertThat(RecruitmentHrSync.shift(null, "DIRECT", null)).isNull();
        assertThat(RecruitmentHrSync.shift("C", "DIRECT", "NUIT")).isNull();
    }

    @Test void detectsTeamLeadersAndGroupsBySupervisorLineAndTeam() {
        assertThat(RecruitmentHrSync.isTeamLeader(" team  Leader ")).isTrue();
        assertThat(RecruitmentHrSync.isTeamLeader("Couturière")).isFalse();
        assertThat(RecruitmentHrSync.canLead("JOB_TEAM_LEADER")).isTrue();
        assertThat(RecruitmentHrSync.canLead("JOB_EMPLOYEE")).isFalse();
        assertThat(RecruitmentHrSync.groupKey(" sup  X ", "p6 av", "a")).isEqualTo("SUP X|P6 AV|A");
        assertThat(RecruitmentHrSync.groupKey("Sup", "Audi", null)).isEqualTo("SUP|AUDI|");
        assertThat(RecruitmentHrSync.groupKey("", "Audi", "A")).isNull();
        assertThat(RecruitmentHrSync.groupKey("Sup", " ", "A")).isNull();
    }

    @Test void parsesWorkbookDates() {
        assertThat(RecruitmentHrSync.date("2024-02-29T08:00:00")).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(RecruitmentHrSync.date("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(RecruitmentHrSync.date("2023-02-30")).isNull();
        assertThat(RecruitmentHrSync.date("#VALUE!")).isNull();
        assertThat(RecruitmentHrSync.date("29/02/2024")).isNull();
    }
}
