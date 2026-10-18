package com.adient.training.service;

import com.adient.training.dto.Dtos.*;
import com.adient.training.entity.AppUser;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Identifiant ou mot de passe incorrect.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtUtil jwtUtil;
    private final int maxFailedAttempts;
    private final int lockMinutes;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtUtil jwtUtil,
                       @Value("${app.security.max-failed-attempts}") int maxFailedAttempts,
                       @Value("${app.security.lock-minutes}") int lockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtUtil = jwtUtil;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest req) {
        AppUser user = userRepository.findByUsernameIgnoreCase(req.username().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED,
                    "Compte temporairement verrouillé suite à plusieurs échecs. Réessayez plus tard.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setFailedAttempts(0);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            userRepository.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Ce compte est désactivé. Contactez un administrateur.");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return new LoginResponse(jwtUtil.generate(user), jwtUtil.getExpirationMs(), UserDto.of(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(load(userId));
    }

    @Transactional
    public UserDto changePassword(Long userId, ChangePasswordRequest req) {
        AppUser user = load(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le mot de passe actuel est incorrect.");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        passwordPolicy.validate(req.newPassword());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        return UserDto.of(userRepository.save(user));
    }

    private AppUser load(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session invalide."));
    }
}
