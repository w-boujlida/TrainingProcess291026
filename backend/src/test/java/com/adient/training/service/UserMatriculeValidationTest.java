package com.adient.training.service;

import com.adient.training.dto.Dtos.CreateUserRequest;
import com.adient.training.entity.Role;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMatriculeValidationTest {
    @Test
    void acceptsExactShortMatriculesAndPreservesExistingZeroes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String matricule : new String[]{"7", "12", "0012", "user.name-1_2", "x".repeat(50)}) {
                var request = new CreateUserRequest(matricule, "Test User", "", Role.FORMATRICE, "Test@2026");
                assertThat(validator.validate(request)).isEmpty();
                assertThat(request.username()).isEqualTo(matricule);
            }
            for (String matricule : new String[]{"", " ", "a b", "x".repeat(51)}) {
                assertThat(validator.validate(new CreateUserRequest(
                        matricule, "Test User", "", Role.FORMATRICE, "Test@2026"))).isNotEmpty();
            }
        }
    }
}
