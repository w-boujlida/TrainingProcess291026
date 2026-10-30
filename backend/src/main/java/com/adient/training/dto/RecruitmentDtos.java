package com.adient.training.dto;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.BookletAssessment;
import com.adient.training.entity.Candidate;
import com.adient.training.entity.CandidateStatus;
import com.adient.training.entity.IntegrationBooklet;
import com.adient.training.entity.IntegrationBookletObjective;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.entity.TrainingLevel;
import com.adient.training.entity.WorkShift;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class RecruitmentDtos {

    private RecruitmentDtos() {}

    private static String objectiveLabelFr(String key) {
        return switch (key) {
            case "company" -> "Découvrir l’entreprise et les règles d’intégration";
            case "safety" -> "Appliquer les règles de santé et de sécurité";
            case "workstation" -> "Identifier le poste, les outils et les équipements";
            case "quality" -> "Respecter les consignes et critères qualité";
            case "operations" -> "Réaliser les opérations selon le mode opératoire";
            case "autonomy" -> "Travailler en autonomie et demander de l’aide si nécessaire";
            default -> throw new IllegalStateException("Objectif de livret inconnu : " + key);
        };
    }

    private static String objectiveLabelAr(String key) {
        return switch (key) {
            case "company" -> "التعرف على الشركة وقواعد الاندماج";
            case "safety" -> "تطبيق قواعد الصحة والسلامة";
            case "workstation" -> "التعرف على المنصب والأدوات والمعدات";
            case "quality" -> "احترام تعليمات ومعايير الجودة";
            case "operations" -> "إنجاز العمليات حسب طريقة العمل";
            case "autonomy" -> "العمل باستقلالية وطلب المساعدة عند الحاجة";
            default -> throw new IllegalStateException("Objectif de livret inconnu : " + key);
        };
    }

    public record CandidateRequest(
            @NotNull(message = "La catégorie est obligatoire.") RecruitmentCategory category,
            @Size(max = 30, message = "Le matricule est trop long.") String matricule,
            @NotBlank(message = "Le nom et prénom sont obligatoires.")
            @Size(max = 120, message = "Le nom est trop long.") String fullName,
            @NotBlank(message = "Le CIN est obligatoire.")
            @Size(max = 20, message = "Le CIN est trop long.") String cin,
            @Past(message = "La date de naissance doit être dans le passé.") LocalDate birthDate,
            @Size(max = 30, message = "Le téléphone est trop long.") String phone,
            @Email(message = "Adresse e-mail invalide.")
            @Size(max = 150, message = "L'adresse e-mail est trop longue.") String email,
            @Size(max = 255, message = "L'adresse est trop longue.") String address,
            @Size(max = 120, message = "Le poste est trop long.") String position,
            @Size(max = 120, message = "Le département est trop long.") String department,
            LocalDate hireDate,
            @Size(max = 120, message = "Le niveau d'étude est trop long.") String educationLevel,
            WorkShift shift,
            Long trainerId,
            Long teamLeaderId,
            CandidateStatus status,
            @Size(max = 2000, message = "Les remarques sont trop longues.") String remarks,
            TrainingLevel trainingLevel) {}

    public record CandidateStatusRequest(@NotNull(message = "Le statut est obligatoire.") CandidateStatus status,
                                         /** Direct position, required when validating a Formation employee. */
                                         String position) {}

    /** Bulk assignment; each field is applied only when its change flag is set (a null value clears it). */
    public record AssignmentRequest(@NotEmpty(message = "Sélectionnez au moins un dossier.")
                                    @Size(max = 5000, message = "Trop de dossiers sélectionnés.") java.util.List<@NotNull Long> ids,
                                    boolean changeShift, WorkShift shift,
                                    boolean changeTeamLeader, Long teamLeaderId) {}

    public record HierarchyMember(Long candidateId, String matricule, String fullName, String category, String position,
                                  String team, String shift, String shiftLabel, Long teamLeaderId, String teamLeaderName,
                                  boolean teamLeaderAuto, Long suggestedTeamLeaderId) {}

    public record HierarchyLeader(Long userId, String fullName, String matricule, String line, String team,
                                  Long candidateId, String shift, String shiftLabel, int members, boolean assignable) {}

    public record HierarchyLine(String line, java.util.List<HierarchyMember> members) {}

    public record HierarchySupervisor(String name, java.util.List<HierarchyLeader> teamLeaders,
                                      java.util.List<HierarchyLine> lines, int members, int withoutTeamLeader) {}

    public record Hierarchy(java.util.List<HierarchySupervisor> supervisors, java.util.List<StaffDto> teamLeaders,
                            int members, int withoutTeamLeader, int withoutShift) {}

    public record BookletObjectiveUpdate(
            @NotBlank String key,
            @NotNull BookletAssessment assessment,
            @Size(max = 1000) String comment) {}

    public record IntegrationBookletUpdateRequest(
            LocalDate trainingStartDate,
            LocalDate trainingEndDate,
            BookletAssessment evaluationOutcome,
            @Size(max = 3000) String evaluationNotes,
            @NotEmpty java.util.List<@Valid BookletObjectiveUpdate> objectives) {}

    public record IntegrationBookletSignatureRequest(
            @NotBlank(message = "La signature est obligatoire.")
            @Size(max = 40000, message = "La signature est trop volumineuse.") String signature) {}

    public record BookletObjectiveDto(String key, String labelFr, String labelAr,
                                      BookletAssessment assessment, String comment) {}

    public record IntegrationBookletDto(
            Long candidateId, String fullName, String matricule, String cin, String category,
            String position, String department, LocalDate hireDate, String trainingLevel,
            Long trainerId, String trainerName, LocalDate trainingStartDate, LocalDate trainingEndDate,
            BookletAssessment evaluationOutcome, String evaluationNotes,
            java.util.List<BookletObjectiveDto> objectives, String candidateSignature,
            String candidateSignedBy, LocalDateTime candidateSignedAt,
            String trainerSignedBy, LocalDateTime trainerSignedAt, String status) {
        public static IntegrationBookletDto of(IntegrationBooklet booklet) {
            Candidate candidate = booklet.getCandidate();
            return new IntegrationBookletDto(candidate.getId(), candidate.getFullName(), candidate.getMatricule(),
                    candidate.getCin(), candidate.getCategory().getLabel(), candidate.getPosition(),
                    candidate.getDepartment(), candidate.getHireDate(),
                    candidate.getTrainingLevel() == null ? null : candidate.getTrainingLevel().getLabel(),
                    candidate.getTrainer() == null ? null : candidate.getTrainer().getId(),
                    candidate.getTrainer() == null ? null : candidate.getTrainer().getFullName(),
                    booklet.getTrainingStartDate(), booklet.getTrainingEndDate(), booklet.getEvaluationOutcome(),
                    booklet.getEvaluationNotes(),
                    booklet.getObjectives().stream().map(o -> new BookletObjectiveDto(
                            o.getObjectiveKey(), objectiveLabelFr(o.getObjectiveKey()),
                            objectiveLabelAr(o.getObjectiveKey()),
                            o.getAssessment(), o.getComment())).toList(),
                    booklet.getCandidateSignature(),
                    booklet.getCandidateSignedBy() == null ? null : booklet.getCandidateSignedBy().getFullName(),
                    booklet.getCandidateSignedAt(),
                    booklet.getTrainerSignedBy() == null ? null : booklet.getTrainerSignedBy().getFullName(),
                    booklet.getTrainerSignedAt(),
                    booklet.getTrainerSignedAt() != null ? "COMPLETED"
                            : booklet.getCandidateSignedAt() != null ? "CANDIDATE_SIGNED" : "IN_PROGRESS");
        }
    }

    public record ImportError(int row, String message) {}

    public record ImportResult(int created, int updated, int skipped, java.util.List<ImportError> errors) {}

    public record StaffDto(Long id, String fullName, String role) {
        public static StaffDto of(AppUser u) {
            return u == null ? null : new StaffDto(u.getId(), u.getFullName(), u.getRole().name());
        }
    }

    public record CandidateDto(Long id, String category, String categoryLabel, String status, String statusLabel,
                               String matricule, String fullName, String cin, LocalDate birthDate, String phone,
                               String email, String address, String position, String department,
                               LocalDate hireDate, String educationLevel, String shift, String shiftLabel,
                               StaffDto trainer, StaffDto teamLeader, boolean teamLeaderAuto, String remarks, String createdBy,
                               LocalDateTime createdAt, LocalDateTime updatedAt, String trainingLevel,
                               String trainingLevelLabel, String trialLabel, LocalDate trialEndDate,
                               LocalDate formationValidatedOn) {
        public static CandidateDto of(Candidate c) {
            TrainingLevel level = c.getTrainingLevel();
            return new CandidateDto(c.getId(), c.getCategory().name(), c.getCategory().getLabel(),
                    c.getStatus().name(), c.getStatus().getLabel(), c.getMatricule(), c.getFullName(), c.getCin(),
                    c.getBirthDate(), c.getPhone(), c.getEmail(), c.getAddress(), c.getPosition(),
                    c.getDepartment(), c.getHireDate(), c.getEducationLevel(),
                    c.getShift() == null ? null : c.getShift().name(),
                    c.getShift() == null ? null : c.getShift().getLabel(),
                    StaffDto.of(c.getTrainer()), StaffDto.of(c.getTeamLeader()), c.isTeamLeaderAuto(), c.getRemarks(),
                    c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt(),
                    level == null ? null : level.name(), level == null ? null : level.getLabel(),
                    level == null ? null : level.getTrialLabel(),
                    level == null ? null : level.trialEnd(c.getHireDate()),
                    c.getFormationValidatedOn());
        }
    }
}
