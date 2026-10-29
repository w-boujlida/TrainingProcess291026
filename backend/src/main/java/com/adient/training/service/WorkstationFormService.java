package com.adient.training.service;

import com.adient.training.dto.WorkstationDtos.*;
import com.adient.training.entity.*;
import com.adient.training.entity.WorkstationCycle.Kind;
import com.adient.training.entity.WorkstationStep.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.UserRepository;
import com.adient.training.repository.WorkstationFormRepository;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkstationFormService {
    private final WorkstationFormRepository forms;
    private final UserRepository users;

    public WorkstationFormService(WorkstationFormRepository forms, UserRepository users) {
        this.forms = forms;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public FormDto get(Long candidateId) { return FormDto.of(find(candidateId)); }

    @Transactional
    public FormDto update(Long candidateId, AuthUser actor, FormUpdate request) {
        WorkstationForm form = find(candidateId);
        requireEditor(form, actor);
        requireVersion(form, request.version());
        Set<Kind> kinds = new HashSet<>();
        boolean changed = false;
        for (CycleUpdate update : request.cycles()) {
            if (!kinds.add(update.kind())) throw badRequest("Chaque cycle doit être fourni une seule fois.");
            WorkstationCycle cycle = cycle(form, update.kind());
            validateDates(update);
            boolean cycleChanged = !Objects.equals(cycle.getStartDate(), update.startDate())
                    || !Objects.equals(cycle.getEndDate(), update.endDate())
                    || !Objects.equals(cycle.getEvaluationDate(), update.evaluationDate())
                    || !Objects.equals(cycle.getMeasures(), clean(update.measures()));
            boolean datesChanged = !Objects.equals(cycle.getStartDate(), update.startDate())
                    || !Objects.equals(cycle.getEndDate(), update.endDate())
                    || !Objects.equals(cycle.getEvaluationDate(), update.evaluationDate());
            Set<Long> ids = new HashSet<>();
            for (StepUpdate incoming : update.steps()) {
                if (!ids.add(incoming.id())) throw badRequest("Une étape est fournie plusieurs fois.");
                WorkstationStep step = step(cycle, incoming.id());
                if (incoming.trainingStatus() != TrainingStatus.X && incoming.evaluationStatus() != null) {
                    throw badRequest("Une évaluation OK / NOK exige une formation réalisée (X).");
                }
                boolean stepChanged = !Objects.equals(step.getDeadline(), incoming.deadline())
                        || step.getTrainingStatus() != incoming.trainingStatus()
                        || step.getEvaluationStatus() != incoming.evaluationStatus();
                if (stepChanged || datesChanged) {
                    step.setSignedBy(null);
                    step.setSignedAt(null);
                }
                step.setDeadline(incoming.deadline());
                step.setTrainingStatus(incoming.trainingStatus());
                step.setEvaluationStatus(incoming.evaluationStatus());
                cycleChanged |= stepChanged;
            }
            if (ids.size() != cycle.getSteps().size()) throw badRequest("Toutes les étapes doivent être fournies.");
            cycle.setStartDate(update.startDate());
            cycle.setEndDate(update.endDate());
            cycle.setEvaluationDate(update.evaluationDate());
            cycle.setMeasures(clean(update.measures()));
            if (cycleChanged) {
                cycle.setOperatorSignature(null);
                cycle.setOperatorSignedAt(null);
                cycle.setOperatorRecordedBy(null);
            }
            changed |= cycleChanged;
        }
        if (kinds.size() != 2) throw badRequest("Les cycles initial et recyclage sont obligatoires.");
        if (changed) form.touch();
        return FormDto.of(forms.saveAndFlush(form));
    }

    @Transactional
    public FormDto signStep(Long candidateId, Kind kind, Long stepId, AuthUser actor, Long version) {
        WorkstationForm form = find(candidateId);
        requireTrainer(form, actor);
        requireVersion(form, version);
        WorkstationCycle cycle = cycle(form, kind);
        WorkstationStep step = step(cycle, stepId);
        if (step.getSignedAt() != null) throw badRequest("Cette étape est déjà signée.");
        if (step.getTrainingStatus() != TrainingStatus.X || cycle.getStartDate() == null || cycle.getEndDate() == null) {
            throw badRequest("Renseignez les dates du cycle et marquez la formation réalisée (X) avant de signer.");
        }
        step.setSignedBy(actorName(actor));
        step.setSignedAt(LocalDateTime.now());
        form.touch();
        return FormDto.of(forms.saveAndFlush(form));
    }

    @Transactional
    public FormDto signOperator(Long candidateId, Kind kind, AuthUser actor, SignatureRequest request) {
        WorkstationForm form = find(candidateId);
        requireEditor(form, actor);
        requireVersion(form, request.version());
        WorkstationCycle cycle = cycle(form, kind);
        if (cycle.getOperatorSignedAt() != null) throw badRequest("L'opérateur a déjà émargé ce cycle.");
        CycleDto result = CycleDto.of(cycle);
        if (cycle.getStartDate() == null || cycle.getEndDate() == null || cycle.getEvaluationDate() == null
                || result.decision().equals("PENDING") || result.decision().equals("NOT_APPLICABLE")
                || cycle.getSteps().stream().anyMatch(s -> s.getTrainingStatus() == TrainingStatus.X && s.getSignedAt() == null)) {
            throw badRequest("Complétez les dates, évaluations et signatures des formations réalisées avant l'émargement.");
        }
        SignatureValidator.validatePng(request.signature());
        cycle.setOperatorSignature(request.signature());
        cycle.setOperatorSignedAt(LocalDateTime.now());
        cycle.setOperatorRecordedBy(actorName(actor));
        form.touch();
        return FormDto.of(forms.saveAndFlush(form));
    }

    private WorkstationForm find(Long id) {
        return forms.findByCandidateId(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "Fiche d'intégration et de formation au poste introuvable."));
    }
    private static WorkstationCycle cycle(WorkstationForm form, Kind kind) {
        return form.getCycles().stream().filter(c -> c.getKind() == kind).findFirst()
                .orElseThrow(() -> badRequest("Cycle de formation invalide."));
    }
    private static WorkstationStep step(WorkstationCycle cycle, Long id) {
        return cycle.getSteps().stream().filter(s -> s.getId().equals(id)).findFirst()
                .orElseThrow(() -> badRequest("L'étape n'appartient pas à ce cycle."));
    }
    private String actorName(AuthUser actor) {
        return users.findById(actor.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "Utilisateur introuvable.")).getFullName();
    }
    private static void requireVersion(WorkstationForm form, Long version) {
        if (version == null || form.getVersion() != version) throw new ApiException(HttpStatus.CONFLICT,
                "La fiche a été modifiée. Rechargez-la avant de continuer.");
    }
    private static void requireEditor(WorkstationForm form, AuthUser actor) {
        if (actor.role() == Role.ADMIN || actor.role() == Role.SUPER_ADMIN) return;
        requireTrainer(form, actor);
    }
    private static void requireTrainer(WorkstationForm form, AuthUser actor) {
        Candidate candidate = form.getCandidate();
        if (actor.role() == Role.FORMATRICE && candidate.getTrainer() != null
                && candidate.getTrainer().getId().equals(actor.id())) return;
        if (actor.role() == Role.TEAM_LEADER && candidate.getTeamLeader() != null
                && candidate.getTeamLeader().getId().equals(actor.id())) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "Seule la formatrice ou le Team Leader affecté peut effectuer cette action.");
    }
    private static void validateDates(CycleUpdate cycle) {
        if ((cycle.startDate() == null) != (cycle.endDate() == null)) {
            throw badRequest("Renseignez les deux dates de formation, ou laissez-les vides.");
        }
        if (cycle.startDate() != null && cycle.endDate().isBefore(cycle.startDate())) {
            throw badRequest("La date de fin doit être postérieure ou égale à la date de début.");
        }
        if (cycle.evaluationDate() != null && (cycle.endDate() == null || cycle.evaluationDate().isBefore(cycle.endDate()))) {
            throw badRequest("La date d'évaluation doit être postérieure ou égale à la fin de formation.");
        }
    }
    private static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
