package com.adient.training.dto;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.Positive;

public final class Dtos {

    private Dtos() {}

    public record LoginRequest(
            @NotBlank(message = "L'identifiant est obligatoire.") @Size(max = 50) String username,
            @NotBlank(message = "Le mot de passe est obligatoire.") String password,
            @Size(max = 100, message = "Le nom du poste ne doit pas dépasser 100 caractères.") String deviceName) {
        public LoginRequest(String username, String password) { this(username, password, null); }
    }

    public record LoginResponse(String token, long expiresInMs, UserDto user) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Le mot de passe actuel est obligatoire.") String currentPassword,
            @NotBlank(message = "Le nouveau mot de passe est obligatoire.") String newPassword) {}

    public record CreateUserRequest(
            @NotBlank(message = "L'identifiant est obligatoire.")
            @Size(min = 1, max = 50, message = "L'identifiant doit contenir entre 1 et 50 caractères.")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "L'identifiant ne peut contenir que lettres, chiffres, '.', '_' ou '-'.")
            String username,
            @NotBlank(message = "Le nom complet est obligatoire.")
            @Size(max = 120, message = "Le nom complet est trop long.")
            String fullName,
            @Email(message = "Adresse e-mail invalide.")
            @Size(max = 150, message = "L'adresse e-mail est trop longue.")
            String email,
            @NotNull(message = "Le rôle est obligatoire.") Role role,
            @NotBlank(message = "Le mot de passe temporaire est obligatoire.") String password,
            @Pattern(regexp = "^(CADRE|DIRECT|INDIRECT)?$", message = "Type de travail invalide.") String workType) {
        public CreateUserRequest(String username, String fullName, String email, Role role, String password) {
            this(username, fullName, email, role, password, null);
        }
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Le nom complet est obligatoire.")
            @Size(max = 120, message = "Le nom complet est trop long.")
            String fullName,
            @Email(message = "Adresse e-mail invalide.")
            @Size(max = 150, message = "L'adresse e-mail est trop longue.")
            String email,
            @NotNull(message = "Le rôle est obligatoire.") Role role,
            @Pattern(regexp = "^(CADRE|DIRECT|INDIRECT)?$", message = "Type de travail invalide.") String workType) {}

    public record StatusRequest(@NotNull(message = "Le statut est obligatoire.") Boolean active) {}

    public record UserSelectionRequest(
            @NotNull @Size(min = 1, max = 10000) List<@NotNull @Positive Long> ids,
            @NotNull @Pattern(regexp = "^(WORK_TYPE|ACTIVATE|DEACTIVATE)$") String action,
            @Pattern(regexp = "^(CADRE|DIRECT|INDIRECT)$") String workType) {}

    public record ResetPasswordResponse(String temporaryPassword) {}

    public record RoleDto(String code, String label) {}

    public record UserDto(Long id, String username, String fullName, String email, String role,
                          String roleLabel, boolean active, boolean mustChangePassword,
                          LocalDateTime lastLoginAt, LocalDateTime createdAt, String workType) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                    u.getRole().name(), u.getRole().getLabel(), u.isActive(), u.isMustChangePassword(),
                    u.getLastLoginAt(), u.getCreatedAt(), u.getWorkType());
        }
    }
}
