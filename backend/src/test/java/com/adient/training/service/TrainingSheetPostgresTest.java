package com.adient.training.service;

import com.adient.training.dto.TrainingSheetDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.CandidateRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class TrainingSheetPostgresTest {

    @Autowired TrainingSheetService service;
    @Autowired CandidateRepository candidates;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    private Long candidateId;
    private AuthUser trainer;
    private AuthUser leader;
    private AuthUser supervisor;
    private AuthUser quality;

    @BeforeEach
    void createEmployee() {
        AppUser trainerAccount = account(Role.FORMATRICE);
        AppUser leaderAccount = account(Role.TEAM_LEADER);
        trainer = actor(trainerAccount);
        leader = actor(leaderAccount);
        supervisor = actor(account(Role.SUPERVISEUR));
        quality = actor(account(Role.QUALITY));
        Candidate candidate = new Candidate();
        candidate.setCategory(RecruitmentCategory.CADRE);
        candidate.setFullName("Test modèle couture");
        candidate.setCin(UUID.randomUUID().toString().substring(0, 18));
        candidate.setDepartment("Projet test / Ligne 1");
        candidate.setTrainer(trainerAccount);
        candidate.setTeamLeader(leaderAccount);
        candidateId = candidates.saveAndFlush(candidate).getId();
        entityManager.clear();
    }

    @Test
    void triggerCreatesExactExcelTemplateWithoutInventedDatesOrScores() {
        TrainingSheetDto sheet = service.get(candidateId);
        assertThat(sheet.department()).isEqualTo("Projet test / Ligne 1");
        assertThat(sheet.periods()).hasSize(5);
        assertThat(sheet.periods()).extracting(PeriodDto::title)
                .containsExactly("Semaine 1", "Semaine 2", "Semaine 3", "Semaine 4", "Semaine 5");
        for (PeriodDto week : sheet.periods()) {
            assertThat(week.startDate()).isNull();
            assertThat(week.endDate()).isNull();
            assertThat(week.competencies()).hasSize(26).allMatch(c -> c.rating() == null);
            assertThat(week.competencies().stream().filter(c -> c.section() == TrainingSection.BEHAVIOR)).hasSize(7);
            assertThat(week.competencies().stream().filter(c -> c.section() == TrainingSection.KNOWLEDGE)).hasSize(4);
            assertThat(week.competencies().stream().filter(c -> c.section() == TrainingSection.MACHINE)).hasSize(4);
            assertThat(week.competencies().stream().filter(c -> c.section() == TrainingSection.SEWING)).hasSize(11);
            assertThat(week.competencies().getLast().label()).isEqualTo("Couture du profil");
        }
    }

    @Test
    void evaluationsMixedSignaturesAndInvalidationPersistAcrossReloads() {
        TrainingSheetDto initial = service.get(candidateId);
        TrainingSheetDto evaluated = service.update(candidateId, leader, evaluatedRequest(initial));
        assertThat(evaluated.version()).isGreaterThan(initial.version());
        List<Long> periodIds = evaluated.periods().stream().map(PeriodDto::id).toList();
        entityManager.clear();
        assertThat(service.get(candidateId).periods().getFirst().competencies().getFirst().rating()).isEqualTo(4);

        for (int week = 0; week < 5; week++) {
            if (week % 2 == 0) service.signTeamLeader(candidateId, periodIds.get(week), leader);
            else service.signTrainer(candidateId, periodIds.get(week), trainer);
            entityManager.clear();
        }
        service.signSupervisor(candidateId, supervisor);
        entityManager.clear();
        service.signQuality(candidateId, quality);
        entityManager.clear();
        TrainingSheetDto signed = service.get(candidateId);
        assertThat(signed.qualitySignedAt()).isNotNull();
        assertThat(signed.periods().getFirst().teamLeaderSignedBy()).isEqualTo(leader.username());
        assertThat(signed.periods().get(1).trainerSignedBy()).isEqualTo(trainer.username());

        service.update(candidateId, trainer, requestFrom(signed));
        entityManager.clear();
        assertThat(service.get(candidateId).qualitySignedAt()).isNotNull();

        List<PeriodUpdate> modified = signed.periods().stream().map(p -> new PeriodUpdate(
                p.id(), p.startDate(), p.endDate(), p.competencies().stream().map(c -> new CompetencyUpdate(
                        c.id(), p.sequenceNo() == 5 ? 5 : c.rating(), c.comment())).toList())).toList();
        service.update(candidateId, trainer, new TrainingSheetUpdateRequest(signed.version(), modified, signed.transferOpinions()));
        entityManager.clear();
        TrainingSheetDto reloaded = service.get(candidateId);
        assertThat(reloaded.periods()).extracting(PeriodDto::id).containsExactlyElementsOf(periodIds);
        assertThat(reloaded.periods().subList(0, 4)).allMatch(p -> p.trainerSignedAt() != null || p.teamLeaderSignedAt() != null);
        assertThat(reloaded.periods().getLast().trainerSignedAt()).isNull();
        assertThat(reloaded.periods().getLast().teamLeaderSignedAt()).isNull();
        assertThat(reloaded.supervisorSignedAt()).isNull();
        assertThat(reloaded.qualitySignedAt()).isNull();
        assertThat(reloaded.periods().getLast().competencies()).allMatch(c -> c.rating() == 5);
        assertThatThrownBy(() -> service.update(candidateId, trainer, evaluatedRequest(initial)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private TrainingSheetUpdateRequest evaluatedRequest(TrainingSheetDto sheet) {
        return new TrainingSheetUpdateRequest(sheet.version(), sheet.periods().stream().map(p -> new PeriodUpdate(
                p.id(), LocalDate.of(2026, 1, 1).plusWeeks(p.sequenceNo() - 1),
                LocalDate.of(2026, 1, 6).plusWeeks(p.sequenceNo() - 1),
                p.competencies().stream().map(c -> new CompetencyUpdate(c.id(), 4, null)).toList())).toList(),
                new TransferOpinions("Favorable", "Acquis", "Acquis", "À confirmer"));
    }

    private TrainingSheetUpdateRequest requestFrom(TrainingSheetDto sheet) {
        return new TrainingSheetUpdateRequest(sheet.version(), sheet.periods().stream().map(p -> new PeriodUpdate(
                p.id(), p.startDate(), p.endDate(), p.competencies().stream()
                .map(c -> new CompetencyUpdate(c.id(), c.rating(), c.comment())).toList())).toList(), sheet.transferOpinions());
    }

    private AppUser account(Role role) {
        AppUser user = new AppUser();
        user.setUsername("test-" + UUID.randomUUID());
        user.setFullName(user.getUsername());
        user.setPasswordHash("unused-in-service-tests");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private AuthUser actor(AppUser user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }
}
