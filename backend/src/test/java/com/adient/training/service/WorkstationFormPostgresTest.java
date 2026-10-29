package com.adient.training.service;

import com.adient.training.dto.WorkstationDtos.*;
import com.adient.training.entity.*;
import com.adient.training.entity.WorkstationCycle.Kind;
import com.adient.training.entity.WorkstationStep.*;
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
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class WorkstationFormPostgresTest {
    @Autowired WorkstationFormService service;
    @Autowired CandidateRepository candidates;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired TrainingSheetService training;
    @Autowired IntegrationBookletService booklets;
    private Long candidateId;
    private AuthUser trainer;
    private AuthUser leader;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        AppUser trainerAccount = user(Role.FORMATRICE);
        AppUser leaderAccount = user(Role.TEAM_LEADER);
        trainer = actor(trainerAccount);
        leader = actor(leaderAccount);
        admin = actor(user(Role.ADMIN));
        Candidate candidate = new Candidate();
        candidate.setCategory(RecruitmentCategory.CADRE);
        candidate.setFullName("Test intégration");
        candidate.setCin(UUID.randomUUID().toString().substring(0, 18));
        candidate.setTrainer(trainerAccount);
        candidate.setTeamLeader(leaderAccount);
        candidateId = candidates.saveAndFlush(candidate).getId();
        entityManager.clear();
    }

    @Test
    void newEmployeesHaveBothBlankCyclesAndOtherDocumentsRemainSeparate() {
        FormDto form = service.get(candidateId);
        assertThat(form.cycles()).extracting(CycleDto::kind).containsExactly(Kind.INITIAL, Kind.REFRESHER);
        assertThat(form.cycles()).allMatch(c -> c.steps().size() == 26 && c.score() == null
                && c.decision().equals("PENDING") && c.operatorSignature() == null && c.startDate() == null);
        assertThat(form.cycles().getFirst().steps()).allMatch(s -> s.trainingStatus() == null && s.evaluationStatus() == null);
        assertThat(form.cycles().getFirst().steps().getLast().label()).contains("chariots");
        assertThat(training.get(candidateId).periods()).hasSize(5);
        assertThat(booklets.get(candidateId).objectives()).hasSize(6);
    }

    @Test
    void exactEightyPercentIsQualifiedAndBelowIsNot() {
        FormDto initial = service.get(candidateId);
        FormDto updated = service.update(candidateId, leader, evaluated(initial, 4, 5));
        assertThat(updated.cycles().getFirst().score()).isEqualByComparingTo("80.00");
        assertThat(updated.cycles().getFirst().decision()).isEqualTo("QUALIFIED");
        entityManager.clear();
        FormDto loaded = service.get(candidateId);
        updated = service.update(candidateId, trainer, evaluated(loaded, 3, 5));
        assertThat(updated.cycles().getFirst().score()).isEqualByComparingTo("60.00");
        assertThat(updated.cycles().getFirst().decision()).isEqualTo("NOT_QUALIFIED");
        assertThat(updated.cycles().getLast().decision()).isEqualTo("PENDING");
    }

    @Test
    void allNotApplicableAndPartialAssessmentsNeverQualify() {
        FormDto initial = service.get(candidateId);
        FormDto na = service.update(candidateId, admin, evaluated(initial, 0, 0));
        assertThat(na.cycles().getFirst().score()).isNull();
        assertThat(na.cycles().getFirst().decision()).isEqualTo("NOT_APPLICABLE");
        FormUpdate complete = evaluated(na, 5, 5);
        FormDto partial = service.update(candidateId, trainer, mapInitial(complete, s -> s.id().equals(na.cycles().getFirst().steps().getFirst().id())
                ? new StepUpdate(s.id(), null, TrainingStatus.X, null) : s));
        assertThat(partial.cycles().getFirst().decision()).isEqualTo("PENDING");
    }

    @Test
    void assignedTrainerAndLeaderSignRowsAndOperatorSignaturePersists() throws Exception {
        FormDto form = service.update(candidateId, leader, evaluated(service.get(candidateId), 1, 1));
        Long stepId = form.cycles().getFirst().steps().getFirst().id();
        FormDto signed = service.signStep(candidateId, Kind.INITIAL, stepId, trainer, form.version());
        assertThat(signed.cycles().getFirst().steps().getFirst().signedBy()).isEqualTo(trainer.username());
        FormDto operator = service.signOperator(candidateId, Kind.INITIAL, admin,
                new SignatureRequest(signed.version(), png()));
        entityManager.clear();
        assertThat(service.get(candidateId).cycles().getFirst().operatorSignature()).isEqualTo(png());
        assertThat(operator.cycles().getFirst().operatorRecordedBy()).isEqualTo(admin.username());
        FormDto noop = service.update(candidateId, trainer, request(operator));
        assertThat(noop.cycles().getFirst().operatorSignedAt()).isNotNull();
        FormDto changed = service.update(candidateId, leader, mapInitial(request(noop),
                s -> s.id().equals(stepId) ? new StepUpdate(s.id(), LocalDate.of(2026, 1, 1), s.trainingStatus(), s.evaluationStatus()) : s));
        assertThat(changed.cycles().getFirst().steps().getFirst().signedAt()).isNull();
        assertThat(changed.cycles().getFirst().operatorSignedAt()).isNull();
        assertThat(changed.cycles().getLast().steps()).allMatch(s -> s.trainingStatus() == null);
        assertThat(service.signStep(candidateId, Kind.INITIAL, stepId, leader, changed.version())
                .cycles().getFirst().steps().getFirst().signedBy()).isEqualTo(leader.username());
    }

    @Test
    void changedMeasuresPreserveRowSignaturesButClearOperatorSignature() throws Exception {
        FormDto form = service.update(candidateId, trainer, evaluated(service.get(candidateId), 1, 1));
        form = service.signStep(candidateId, Kind.INITIAL, form.cycles().getFirst().steps().getFirst().id(), leader, form.version());
        form = service.signOperator(candidateId, Kind.INITIAL, leader, new SignatureRequest(form.version(), png()));
        FormUpdate original = request(form);
        CycleUpdate cycle = original.cycles().getFirst();
        FormDto updated = service.update(candidateId, admin, new FormUpdate(form.version(), List.of(
                new CycleUpdate(cycle.kind(), cycle.startDate(), cycle.endDate(), cycle.evaluationDate(), "Recyclage prévu", cycle.steps()),
                original.cycles().getLast())));
        assertThat(updated.cycles().getFirst().steps().getFirst().signedAt()).isNotNull();
        assertThat(updated.cycles().getFirst().operatorSignature()).isNull();
    }

    @Test
    void datesAndEvaluationConsistencyAreEnforced() {
        FormDto form = service.get(candidateId);
        FormUpdate original = request(form);
        assertStatus(() -> service.update(candidateId, trainer, mapInitial(original,
                s -> new StepUpdate(s.id(), null, TrainingStatus.NA, EvaluationStatus.OK))), HttpStatus.BAD_REQUEST);
        CycleUpdate initial = original.cycles().getFirst();
        FormUpdate badDates = new FormUpdate(form.version(), List.of(
                new CycleUpdate(Kind.INITIAL, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 1),
                        null, null, initial.steps()), original.cycles().getLast()));
        assertStatus(() -> service.update(candidateId, trainer, badDates), HttpStatus.BAD_REQUEST);
        FormUpdate earlyEvaluation = new FormUpdate(form.version(), List.of(
                new CycleUpdate(Kind.INITIAL, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5),
                        LocalDate.of(2026, 1, 4), null, initial.steps()), original.cycles().getLast()));
        assertStatus(() -> service.update(candidateId, trainer, earlyEvaluation), HttpStatus.BAD_REQUEST);
    }

    @Test
    void permissionsVersionsAndForeignIdsAreEnforced() {
        FormDto form = service.get(candidateId);
        AuthUser other = actor(user(Role.TEAM_LEADER));
        assertStatus(() -> service.update(candidateId, other, request(form)), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.signStep(candidateId, Kind.INITIAL, form.cycles().getFirst().steps().getFirst().id(),
                admin, form.version()), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.update(candidateId, trainer, new FormUpdate(999L, request(form).cycles())), HttpStatus.CONFLICT);
        assertStatus(() -> service.signStep(candidateId, Kind.INITIAL, form.cycles().getLast().steps().getFirst().id(),
                trainer, form.version()), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.signOperator(candidateId, Kind.INITIAL, admin,
                new SignatureRequest(form.version(), "bad")), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.update(candidateId, trainer, new FormUpdate(form.version(),
                List.of(request(form).cycles().getFirst(), request(form).cycles().getFirst()))), HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedPngIsRejected() {
        assertStatus(() -> SignatureValidator.validatePng("data:image/png;base64,garbage"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> SignatureValidator.validatePng("data:image/jpeg;base64,abcd"), HttpStatus.BAD_REQUEST);
    }

    private FormUpdate evaluated(FormDto form, int ok, int applicable) {
        CycleDto initial = form.cycles().getFirst();
        List<StepUpdate> steps = initial.steps().stream().map(s -> new StepUpdate(s.id(), null,
                s.sequenceNo() <= applicable ? TrainingStatus.X : TrainingStatus.NA,
                s.sequenceNo() <= applicable ? s.sequenceNo() <= ok ? EvaluationStatus.OK : EvaluationStatus.NOK : null)).toList();
        return new FormUpdate(form.version(), List.of(new CycleUpdate(Kind.INITIAL, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), null, steps), request(form).cycles().getLast()));
    }
    private FormUpdate request(FormDto form) {
        return new FormUpdate(form.version(), form.cycles().stream().map(c -> new CycleUpdate(c.kind(), c.startDate(),
                c.endDate(), c.evaluationDate(), c.measures(), c.steps().stream().map(s -> new StepUpdate(s.id(),
                s.deadline(), s.trainingStatus(), s.evaluationStatus())).toList())).toList());
    }
    private FormUpdate mapInitial(FormUpdate original, UnaryOperator<StepUpdate> map) {
        CycleUpdate cycle = original.cycles().getFirst();
        return new FormUpdate(original.version(), List.of(new CycleUpdate(cycle.kind(), cycle.startDate(), cycle.endDate(),
                cycle.evaluationDate(), cycle.measures(), cycle.steps().stream().map(map).toList()), original.cycles().getLast()));
    }
    private AppUser user(Role role) {
        AppUser user = new AppUser();
        user.setUsername("test-" + UUID.randomUUID());
        user.setFullName(user.getUsername());
        user.setRole(role);
        user.setPasswordHash("unused-in-service-tests");
        return users.saveAndFlush(user);
    }
    private AuthUser actor(AppUser user) { return new AuthUser(user.getId(), user.getUsername(), user.getRole()); }
    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }
    private String png() throws Exception {
        BufferedImage image = new BufferedImage(100, 30, BufferedImage.TYPE_INT_ARGB);
        for (int x = 10; x < 80; x++) image.setRGB(x, 15, 0xff123456);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
