package com.adient.training.service;

import com.adient.training.dto.Dtos.*;
import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAllByOrderByFullNameAsc().stream().map(UserDto::of).toList();
    }

    public List<RoleDto> assignableRoles(AuthUser actor) {
        return Arrays.stream(Role.values())
                .filter(actor.role()::canManage)
                .map(r -> new RoleDto(r.name(), r.getLabel()))
                .toList();
    }

    @Transactional
    public UserDto create(AuthUser actor, CreateUserRequest req) {
        requireCanManage(actor, req.role());
        String username = req.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cet identifiant est déjà utilisé.");
        }
        passwordPolicy.validate(req.password());
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(req.fullName().trim());
        user.setEmail(blankToNull(req.email()));
        user.setRole(req.role());
        user.setWorkType(blankToNull(req.workType()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(true);
        return UserDto.of(userRepository.save(user));
    }

    @Transactional
    public UserDto update(AuthUser actor, Long id, UpdateUserRequest req) {
        AppUser user = loadManageable(actor, id);
        if (user.getRole() != req.role()) {
            if (user.getId().equals(actor.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas modifier votre propre rôle.");
            }
            requireCanManage(actor, req.role());
            ensureNotLastSuperAdmin(user);
        }
        user.setFullName(req.fullName().trim());
        user.setEmail(blankToNull(req.email()));
        user.setRole(req.role());
        user.setWorkType(blankToNull(req.workType()));
        return UserDto.of(userRepository.save(user));
    }

    @Transactional
    public UserDto setStatus(AuthUser actor, Long id, boolean active) {
        AppUser user = loadManageable(actor, id);
        if (user.getId().equals(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas désactiver votre propre compte.");
        }

        if (!active) ensureNotLastSuperAdmin(user);
        user.setActive(active);
        if (active) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }
        return UserDto.of(userRepository.save(user));
    }

    @Transactional
    public List<UserDto> applySelection(AuthUser actor, UserSelectionRequest req) {
        if (req == null || req.ids() == null || req.ids().isEmpty() || req.ids().size() > 10000
                || req.ids().stream().anyMatch(id -> id == null || id <= 0)
                || req.action() == null || !Set.of("WORK_TYPE", "ACTIVATE", "DEACTIVATE").contains(req.action())
                || ("WORK_TYPE".equals(req.action()) && !Set.of("CADRE", "DIRECT", "INDIRECT")
                        .contains(req.workType() == null ? "" : req.workType()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sélection ou action invalide.");
        }
        var targets = new LinkedHashSet<>(req.ids()).stream().map(id -> {
            if (id.equals(actor.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas modifier votre propre compte en lot.");
            }
            return loadManageable(actor, id);
        }).toList();
        if ("DEACTIVATE".equals(req.action())) {
            long removing = targets.stream().filter(u -> u.getRole() == Role.SUPER_ADMIN && u.isActive()).count();
            if (removing > 0 && userRepository.countByRoleAndActiveTrue(Role.SUPER_ADMIN) <= removing) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Impossible : il doit rester au moins un Super Admin actif.");
            }
        }
        for (var user : targets) {
            if ("WORK_TYPE".equals(req.action())) {
                user.setWorkType(req.workType());
            } else {
                boolean active = "ACTIVATE".equals(req.action());
                user.setActive(active);
                if (active) {
                    user.setFailedAttempts(0);
                    user.setLockedUntil(null);
                }
            }
        }
        return userRepository.saveAll(targets).stream().map(UserDto::of).toList();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(AuthUser actor, Long id) {
        AppUser user = loadManageable(actor, id);
        String temp = passwordPolicy.generateTemporary();
        user.setPasswordHash(passwordEncoder.encode(temp));
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return new ResetPasswordResponse(temp);
    }

    @Transactional
    public void delete(AuthUser actor, Long id) {
        AppUser user = loadManageable(actor, id);
        if (user.getId().equals(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte.");
        }
        ensureNotLastSuperAdmin(user);
        userRepository.delete(user);
    }

    private AppUser loadManageable(AuthUser actor, Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
        if (!user.getId().equals(actor.id())) requireCanManage(actor, user.getRole());
        return user;
    }

    private void requireCanManage(AuthUser actor, Role target) {
        if (!actor.role().canManage(target)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Vous n'avez pas les droits pour gérer un compte « " + target.getLabel() + " ».");
        }
    }

    private void ensureNotLastSuperAdmin(AppUser user) {
        if (user.getRole() == Role.SUPER_ADMIN && user.isActive()
                && userRepository.countByRoleAndActiveTrue(Role.SUPER_ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Impossible : il doit rester au moins un Super Admin actif.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
