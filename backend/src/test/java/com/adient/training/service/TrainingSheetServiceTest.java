package com.adient.training.service;

import com.adient.training.dto.TrainingSheetDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.TrainingSheetRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingSheetServiceTest {

    private TrainingSheetRepository repository;
    private UserRepository users;
    private TrainingSheetService service;
    private TrainingSheet sheet;
    private final AuthUser trainer = new AuthUser(10L, "trainer", Role.FORMATRICE);
    private final AuthUser leader = new AuthUser(11L, "leader", Role.TEAM_LEADER);
    private final AuthUser supervisor = new AuthUser(12L, "supervisor", Role.SUPERVISEUR);
    private final AuthUser quality = new AuthUser(13L, "quality", Role.QUALITY);

    @BeforeEach
    void setUp() {
        repository = mock(TrainingSheetRepository.class);
        users = mock(UserRepository.class);
        service = new TrainingSheetService(repository, users);
        sheet = new TrainingSheet();
        Candidate candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 1L);
        candidate.setFullName("Salariée test");
        candidate.setTrainer(account(trainer));
        candidate.setTeamLeader(account(leader));
        sheet.setCandidate(candidate);
        for (int week = 0; week < 5; week++) {
            TrainingPeriod period = new TrainingPeriod();
            ReflectionTestUtils.setField(period, "id", (long) week + 1);
            period.setTitle("Semaine " + (week + 1));
            period.setSequenceNo(week + 1);
            period.setSheet(sheet);
            period.setStartDate(LocalDate.of(2026, 1, 1).plusWeeks(week));
            period.setEndDate(period.getStartDate().plusDays(5));
            for (int index = 0; index < 26; index++) {
                TrainingCompetency competency = new TrainingCompetency();
                ReflectionTestUtils.setField(competency, "id", week * 100L + index + 1);
                competency.setPeriod(period);
                competency.setSequenceNo(index + 1);
                competency.setSection(index < 7 ? TrainingSection.BEHAVIOR : index < 11
                        ? TrainingSection.KNOWLEDGE : index < 15 ? TrainingSection.MACHINE : TrainingSection.SEWING);
                competency.setLabel("Critère " + (index + 1));
                competency.setRating(3);
                period.getCompetencies().add(competency);
            }
            sheet.getPeriods().add(period);
        }
        when(repository.findByCandidateId(1L)).thenReturn(Optional.of(sheet));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.of(account(new AuthUser(id, "signer", Role.FORMATRICE)));
        });
    }

    @Test
    void leaderCanEvaluateAndApproveWithoutTrainerSignature() {
        service.update(1L, leader, request());
        TrainingSheetDto result = service.signTeamLeader(1L, 1L, leader);
        assertThat(result.periods().getFirst().teamLeaderSignedAt()).isNotNull();
        assertThat(result.periods().getFirst().trainerSignedAt()).isNull();
    }

    @Test
    void trainerCanApproveWithoutLeaderSignature() {
        TrainingSheetDto result = service.signTrainer(1L, 1L, trainer);
        assertThat(result.periods().getFirst().trainerSignedAt()).isNotNull();
        assertThat(result.periods().getFirst().teamLeaderSignedAt()).isNull();
    }

    @Test
    void secondWeeklySignatureIsRejected() {
        service.signTrainer(1L, 1L, trainer);
        assertBadRequest(() -> service.signTeamLeader(1L, 1L, leader));
    }

    @Test
    void cannotSkipAWeek() {
        assertBadRequest(() -> service.signTrainer(1L, 2L, trainer));
        assertBadRequest(() -> service.signTeamLeader(1L, 2L, leader));
    }

    @Test
    void everyCriterionAndBothDatesAreRequiredForApproval() {
        sheet.getPeriods().getFirst().getCompetencies().getLast().setRating(null);
        assertBadRequest(() -> service.signTrainer(1L, 1L, trainer));
        sheet.getPeriods().getFirst().getCompetencies().getLast().setRating(3);
        sheet.getPeriods().getFirst().setEndDate(null);
        assertBadRequest(() -> service.signTeamLeader(1L, 1L, leader));
    }

    @Test
    void onlyAssignedOperationalUsersMayEvaluateAndSign() {
        AuthUser otherTrainer = new AuthUser(99L, "other", Role.FORMATRICE);
        AuthUser otherLeader = new AuthUser(99L, "other", Role.TEAM_LEADER);
        assertForbidden(() -> service.update(1L, otherTrainer, request()));
        assertForbidden(() -> service.update(1L, otherLeader, request()));
        assertForbidden(() -> service.signTrainer(1L, 1L, otherTrainer));
        assertForbidden(() -> service.signTeamLeader(1L, 1L, otherLeader));
        assertForbidden(() -> service.update(1L, supervisor, request()));
        assertForbidden(() -> service.update(1L, quality, request()));
        assertForbidden(() -> service.signTrainer(1L, 1L, leader));
    }

    @Test
    void mixedWeeklyApprovalsPermitSupervisorThenQuality() {
        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) service.signTrainer(1L, (long) i + 1, trainer);
            else service.signTeamLeader(1L, (long) i + 1, leader);
        }
        assertBadRequest(() -> service.signQuality(1L, quality));
        assertThat(service.signSupervisor(1L, supervisor).supervisorSignedAt()).isNotNull();
        assertThat(service.signQuality(1L, quality).qualitySignedAt()).isNotNull();
        assertBadRequest(() -> service.signSupervisor(1L, supervisor));
        assertBadRequest(() -> service.signQuality(1L, quality));
    }

    @Test
    void supervisorCannotSignIncompleteWeeks() {
        service.signTeamLeader(1L, 1L, leader);
        assertBadRequest(() -> service.signSupervisor(1L, supervisor));
        assertBadRequest(() -> service.signQuality(1L, quality));
        assertForbidden(() -> service.signSupervisor(1L, trainer));
        assertForbidden(() -> service.signQuality(1L, supervisor));
    }

    @Test
    void savingNextWeekPreservesEarlierApprovalsAndPersistentIds() {
        approveAll();
        TrainingSheetUpdateRequest update = withRating(request(), 1, 0, 4);
        service.update(1L, trainer, update);
        assertThat(sheet.getPeriods().getFirst().getTrainerSignedAt()).isNotNull();
        assertThat(sheet.getPeriods().subList(1, 5)).allMatch(p -> p.getTrainerSignedAt() == null && p.getTeamLeaderSignedAt() == null);
        assertThat(sheet.getSupervisorSignedAt()).isNull();
        assertThat(sheet.getQualitySignedAt()).isNull();
        assertThat(sheet.getPeriods().get(1).getId()).isEqualTo(2L);
        assertThat(sheet.getPeriods().get(1).getCompetencies().getFirst().getRating()).isEqualTo(4);
    }

    @Test
    void noOpSavePreservesAllSignatures() {
        approveAll();
        service.update(1L, trainer, request());
        assertThat(sheet.getPeriods()).allMatch(p -> p.getTrainerSignedAt() != null);
        assertThat(sheet.getQualitySignedAt()).isNotNull();
    }

    @Test
    void opinionChangeOnlyResetsFinalSignatures() {
        approveAll();
        TrainingSheetUpdateRequest original = request();
        service.update(1L, leader, new TrainingSheetUpdateRequest(0L, original.periods(),
                new TransferOpinions("Favorable", null, null, null)));
        assertThat(sheet.getPeriods()).allMatch(p -> p.getTrainerSignedAt() != null);
        assertThat(sheet.getBehaviorOpinion()).isEqualTo("Favorable");
        assertThat(sheet.getSupervisorSignedAt()).isNull();
        assertThat(sheet.getQualitySignedAt()).isNull();
    }

    @Test
    void blankDraftCanBeSavedWithoutInventingDatesOrNotes() {
        sheet.getPeriods().forEach(p -> {
            p.setStartDate(null);
            p.setEndDate(null);
            p.getCompetencies().forEach(c -> c.setRating(null));
        });
        TrainingSheetDto result = service.update(1L, trainer, request());
        assertThat(result.periods()).allMatch(p -> p.startDate() == null && p.endDate() == null);
        assertThat(result.periods().getFirst().competencies()).allMatch(c -> c.rating() == null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1, 100})
    void outOfRangeRatingsAreRejected(int rating) {
        assertBadRequest(() -> service.update(1L, trainer, withRating(request(), 0, 0, rating)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void allFiveRatingValuesAreAccepted(int rating) {
        TrainingSheetDto result = service.update(1L, trainer, withRating(request(), 0, 0, rating));
        assertThat(result.periods().getFirst().competencies().getFirst().rating()).isEqualTo(rating);
    }

    @Test
    void overlappingReversedOrPartialDatesAreRejected() {
        TrainingSheetUpdateRequest original = request();
        List<PeriodUpdate> periods = new ArrayList<>(original.periods());
        PeriodUpdate second = periods.get(1);
        periods.set(1, new PeriodUpdate(second.id(), periods.getFirst().endDate(), second.endDate(), second.competencies()));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, periods, original.transferOpinions())));
        periods.set(1, new PeriodUpdate(second.id(), second.endDate(), second.startDate(), second.competencies()));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, periods, original.transferOpinions())));
        periods.set(1, new PeriodUpdate(second.id(), second.startDate(), null, second.competencies()));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, periods, original.transferOpinions())));
    }

    @Test
    void foreignIdsAndMissingWeeksAreRejected() {
        TrainingSheetUpdateRequest original = request();
        List<PeriodUpdate> periods = new ArrayList<>(original.periods());
        PeriodUpdate first = periods.getFirst();
        periods.set(0, new PeriodUpdate(999L, first.startDate(), first.endDate(), first.competencies()));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, periods, original.transferOpinions())));
        List<CompetencyUpdate> criteria = new ArrayList<>(first.competencies());
        criteria.set(0, new CompetencyUpdate(999L, 3, null));
        periods.set(0, new PeriodUpdate(first.id(), first.startDate(), first.endDate(), criteria));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, periods, original.transferOpinions())));
        assertBadRequest(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(0L, List.of(first), original.transferOpinions())));
    }

    @Test
    void staleUpdatesReturnConflict() {
        TrainingSheetUpdateRequest original = request();
        assertThatThrownBy(() -> service.update(1L, trainer, new TrainingSheetUpdateRequest(99L,
                original.periods(), original.transferOpinions()))).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private TrainingSheetUpdateRequest request() {
        return new TrainingSheetUpdateRequest(0L, sheet.getPeriods().stream().map(p -> new PeriodUpdate(
                p.getId(), p.getStartDate(), p.getEndDate(), p.getCompetencies().stream()
                .map(c -> new CompetencyUpdate(c.getId(), c.getRating(), c.getComment())).toList())).toList(),
                new TransferOpinions(sheet.getBehaviorOpinion(), sheet.getKnowledgeOpinion(),
                        sheet.getMachineOpinion(), sheet.getSewingOpinion()));
    }

    private TrainingSheetUpdateRequest withRating(TrainingSheetUpdateRequest request, int week, int criterion, int rating) {
        List<PeriodUpdate> periods = new ArrayList<>(request.periods());
        PeriodUpdate period = periods.get(week);
        List<CompetencyUpdate> competencies = new ArrayList<>(period.competencies());
        CompetencyUpdate competency = competencies.get(criterion);
        competencies.set(criterion, new CompetencyUpdate(competency.id(), rating, competency.comment()));
        periods.set(week, new PeriodUpdate(period.id(), period.startDate(), period.endDate(), competencies));
        return new TrainingSheetUpdateRequest(request.version(), periods, request.transferOpinions());
    }

    private void approveAll() {
        sheet.getPeriods().forEach(p -> {
            p.setTrainerSignedBy(account(trainer));
            p.setTrainerSignedAt(LocalDateTime.now());
        });
        sheet.setSupervisorSignedBy(account(supervisor));
        sheet.setSupervisorSignedAt(LocalDateTime.now());
        sheet.setQualitySignedBy(account(quality));
        sheet.setQualitySignedAt(LocalDateTime.now());
    }

    private AppUser account(AuthUser actor) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", actor.id());
        user.setUsername(actor.username());
        user.setFullName(actor.username());
        user.setRole(actor.role());
        return user;
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
