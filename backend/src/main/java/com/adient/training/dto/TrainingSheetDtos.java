package com.adient.training.dto;

import com.adient.training.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TrainingSheetDtos {

    private TrainingSheetDtos() {}

    public record CompetencyUpdate(
            @NotNull Long id,
            @Min(1) @Max(5) Integer rating,
            @Size(max = 1000) String comment) {}

    public record PeriodUpdate(
            @NotNull Long id, LocalDate startDate, LocalDate endDate,
            @NotNull @Size(min = 26, max = 26) List<@NotNull @Valid CompetencyUpdate> competencies) {}

    public record TransferOpinions(
            @Size(max = 1000) String behavior,
            @Size(max = 1000) String knowledge,
            @Size(max = 1000) String machine,
            @Size(max = 1000) String sewing) {}

    public record TrainingSheetUpdateRequest(
            @NotNull Long version,
            @NotNull @Size(min = 5, max = 5) List<@NotNull @Valid PeriodUpdate> periods,
            @NotNull @Valid TransferOpinions transferOpinions) {}

    public record CompetencyDto(Long id, TrainingSection section, String label, Integer rating, String comment) {}

    public record PeriodDto(
            Long id, String title, int sequenceNo, LocalDate startDate, LocalDate endDate,
            List<CompetencyDto> competencies,
            String trainerSignedBy, LocalDateTime trainerSignedAt,
            String teamLeaderSignedBy, LocalDateTime teamLeaderSignedAt) {}

    public record TrainingSheetDto(
            Long candidateId, long version, String fullName, String matricule, String cin, String position,
            String department, LocalDate hireDate, String trainerName, Long trainerId,
            String teamLeaderName, Long teamLeaderId, List<PeriodDto> periods, TransferOpinions transferOpinions,
            String supervisorSignedBy, LocalDateTime supervisorSignedAt,
            String qualitySignedBy, LocalDateTime qualitySignedAt) {

        public static TrainingSheetDto of(TrainingSheet sheet) {
            Candidate candidate = sheet.getCandidate();
            return new TrainingSheetDto(
                    candidate.getId(), sheet.getVersion(), candidate.getFullName(), candidate.getMatricule(), candidate.getCin(),
                    candidate.getPosition(), candidate.getDepartment(), candidate.getHireDate(),
                    candidate.getTrainer() == null ? null : candidate.getTrainer().getFullName(),
                    candidate.getTrainer() == null ? null : candidate.getTrainer().getId(),
                    candidate.getTeamLeader() == null ? null : candidate.getTeamLeader().getFullName(),
                    candidate.getTeamLeader() == null ? null : candidate.getTeamLeader().getId(),
                    sheet.getPeriods().stream().map(period -> new PeriodDto(
                            period.getId(), period.getTitle(), period.getSequenceNo(), period.getStartDate(),
                            period.getEndDate(),
                            period.getCompetencies().stream().map(competency -> new CompetencyDto(
                                    competency.getId(), competency.getSection(), competency.getLabel(),
                                    competency.getRating(), competency.getComment())).toList(),
                            period.getTrainerSignedBy() == null ? null : period.getTrainerSignedBy().getFullName(),
                            period.getTrainerSignedAt(),
                            period.getTeamLeaderSignedBy() == null ? null : period.getTeamLeaderSignedBy().getFullName(),
                            period.getTeamLeaderSignedAt())).toList(),
                    new TransferOpinions(sheet.getBehaviorOpinion(), sheet.getKnowledgeOpinion(),
                            sheet.getMachineOpinion(), sheet.getSewingOpinion()),
                    sheet.getSupervisorSignedBy() == null ? null : sheet.getSupervisorSignedBy().getFullName(),
                    sheet.getSupervisorSignedAt(),
                    sheet.getQualitySignedBy() == null ? null : sheet.getQualitySignedBy().getFullName(),
                    sheet.getQualitySignedAt());
        }
    }
}
