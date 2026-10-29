package com.adient.training.dto;

import com.adient.training.entity.*;
import com.adient.training.entity.WorkstationCycle.Kind;
import com.adient.training.entity.WorkstationStep.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkstationDtos {
    private WorkstationDtos() {}

    public record StepUpdate(@NotNull Long id, LocalDate deadline,
                             TrainingStatus trainingStatus, EvaluationStatus evaluationStatus) {}
    public record CycleUpdate(@NotNull Kind kind, LocalDate startDate, LocalDate endDate,
                              LocalDate evaluationDate, @Size(max = 3000) String measures,
                              @NotNull @Size(min = 26, max = 26) List<@NotNull @Valid StepUpdate> steps) {}
    public record FormUpdate(@NotNull Long version,
                             @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid CycleUpdate> cycles) {}
    public record SignatureRequest(@NotNull Long version,
                                   @NotBlank @Size(max = 40000) String signature) {}
    public record ApprovalRequest(@NotNull Long version) {}

    public record StepDto(Long id, int sequenceNo, String label, String applicability, String concernedPositions,
                          LocalDate deadline, TrainingStatus trainingStatus, EvaluationStatus evaluationStatus,
                          String signedBy, LocalDateTime signedAt) {}
    public record CycleDto(Kind kind, LocalDate startDate, LocalDate endDate, LocalDate evaluationDate,
                           String measures, List<StepDto> steps, int evaluated, int successful, BigDecimal score,
                           String decision, String operatorSignature, LocalDateTime operatorSignedAt,
                           String operatorRecordedBy) {
        public static CycleDto of(WorkstationCycle cycle) {
            int evaluated = (int) cycle.getSteps().stream()
                    .filter(s -> s.getTrainingStatus() == TrainingStatus.X && s.getEvaluationStatus() != null).count();
            int successful = (int) cycle.getSteps().stream()
                    .filter(s -> s.getTrainingStatus() == TrainingStatus.X && s.getEvaluationStatus() == EvaluationStatus.OK).count();
            boolean complete = cycle.getSteps().stream().allMatch(s -> s.getTrainingStatus() == TrainingStatus.NA
                    || (s.getTrainingStatus() == TrainingStatus.X && s.getEvaluationStatus() != null));
            BigDecimal score = evaluated == 0 ? null : BigDecimal.valueOf(successful * 100L)
                    .divide(BigDecimal.valueOf(evaluated), 2, RoundingMode.HALF_UP);
            String decision = !complete ? "PENDING" : evaluated == 0 ? "NOT_APPLICABLE"
                    : successful * 100 >= evaluated * 80 ? "QUALIFIED" : "NOT_QUALIFIED";
            return new CycleDto(cycle.getKind(), cycle.getStartDate(), cycle.getEndDate(), cycle.getEvaluationDate(),
                    cycle.getMeasures(), cycle.getSteps().stream().map(s -> new StepDto(s.getId(), s.getSequenceNo(),
                    s.getLabel(), s.getApplicability(), s.getConcernedPositions(), s.getDeadline(),
                    s.getTrainingStatus(), s.getEvaluationStatus(), s.getSignedBy(), s.getSignedAt())).toList(),
                    evaluated, successful, score, decision, cycle.getOperatorSignature(), cycle.getOperatorSignedAt(),
                    cycle.getOperatorRecordedBy());
        }
    }
    public record FormDto(Long candidateId, long version, String fullName, String matricule, String position,
                          String department, Long trainerId, Long teamLeaderId, List<CycleDto> cycles) {
        public static FormDto of(WorkstationForm form) {
            Candidate c = form.getCandidate();
            return new FormDto(c.getId(), form.getVersion(), c.getFullName(), c.getMatricule(), c.getPosition(),
                    c.getDepartment(), c.getTrainer() == null ? null : c.getTrainer().getId(),
                    c.getTeamLeader() == null ? null : c.getTeamLeader().getId(),
                    form.getCycles().stream().map(CycleDto::of).toList());
        }
    }
}
