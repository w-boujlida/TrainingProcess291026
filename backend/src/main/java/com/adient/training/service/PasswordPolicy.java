package com.adient.training.service;

import com.adient.training.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasswordPolicy {

    public static final String RULES =
            "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial.";

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "@#$%&*!?";
    private final SecureRandom random = new SecureRandom();

    public void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, RULES);
        }
    }

    public String generateTemporary() {
        String all = UPPER + LOWER + DIGITS + SPECIAL;
        StringBuilder sb = new StringBuilder();
        sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(SPECIAL));
        for (int i = 0; i < 8; i++) sb.append(pick(all));
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = chars[i]; chars[i] = chars[j]; chars[j] = t;
        }
        return new String(chars);
    }

    private char pick(String s) {
        return s.charAt(random.nextInt(s.length()));
    }
}
