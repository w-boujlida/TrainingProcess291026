package com.adient.training.config;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the initial Super Admin account when none exists. */
@Component
public class SuperAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username}") private String username;
    @Value("${app.super-admin.password}") private String password;
    @Value("${app.super-admin.full-name}") private String fullName;

    public SuperAdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) return;
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(Role.SUPER_ADMIN);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        userRepository.save(user);
        log.info("Compte Super Admin initial créé : '{}' (changement de mot de passe requis à la 1re connexion).", username);
    }
}
