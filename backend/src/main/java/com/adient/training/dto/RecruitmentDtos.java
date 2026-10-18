package com.adient.training.dto;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Candidate;
import com.adient.training.entity.CandidateStatus;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.entity.WorkShift;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class RecruitmentDtos {

    private RecruitmentDtos() {}

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
            @Size(max = 2000, message = "Les remarques sont trop longues.") String remarks) {}

    public record CandidateStatusRequest(@NotNull(message = "Le statut est obligatoire.") CandidateStatus status) {}

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
                               StaffDto trainer, StaffDto teamLeader, String remarks, String createdBy,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static CandidateDto of(Candidate c) {
            return new CandidateDto(c.getId(), c.getCategory().name(), c.getCategory().getLabel(),
                    c.getStatus().name(), c.getStatus().getLabel(), c.getMatricule(), c.getFullName(), c.getCin(),
                    c.getBirthDate(), c.getPhone(), c.getEmail(), c.getAddress(), c.getPosition(),
                    c.getDepartment(), c.getHireDate(), c.getEducationLevel(),
                    c.getShift() == null ? null : c.getShift().name(),
                    c.getShift() == null ? null : c.getShift().getLabel(),
                    StaffDto.of(c.getTrainer()), StaffDto.of(c.getTeamLeader()), c.getRemarks(),
                    c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
