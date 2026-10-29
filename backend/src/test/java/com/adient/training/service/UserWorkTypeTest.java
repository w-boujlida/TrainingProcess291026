package com.adient.training.service;

import com.adient.training.dto.Dtos.CreateUserRequest;
import com.adient.training.dto.Dtos.UpdateUserRequest;
import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserWorkTypeTest {
    @Test void validatesOnlyTheThreeOptionalWorkTypes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String value : new String[]{null, "", "CADRE", "DIRECT", "INDIRECT"}) {
                assertThat(factory.getValidator().validate(new CreateUserRequest(
                        "12", "Test User", "", Role.JOB_COUTURIERE, "Test@2026", value))).isEmpty();
            }
            for (String value : new String[]{"OTHER", "direct", "DIRECTS", " "}) {
                assertThat(factory.getValidator().validate(new UpdateUserRequest(
                        "Test User", "", Role.JOB_COUTURIERE, value))).isNotEmpty();
            }
        }
    }

    @Test void createsUpdatesAndClearsWorkTypeWithoutChangingRole() {
        var repo = mock(UserRepository.class);
        var service = new UserService(repo, mock(PasswordEncoder.class), mock(PasswordPolicy.class));
        when(repo.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        var actor = new AuthUser(1L, "admin", Role.SUPER_ADMIN);
        var result = service.create(actor, new CreateUserRequest(
                "12", "Test User", "", Role.JOB_COUTURIERE, "Test@2026", "DIRECT"));
        assertThat(result.workType()).isEqualTo("DIRECT");
        assertThat(result.role()).isEqualTo("JOB_COUTURIERE");
        var user = new AppUser();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 2L);
        user.setRole(Role.JOB_COUTURIERE);
        user.setWorkType("DIRECT");
        when(repo.findById(2L)).thenReturn(Optional.of(user));
        assertThat(service.update(actor, 2L, new UpdateUserRequest(
                "Test User", "", Role.JOB_COUTURIERE, "CADRE")).workType()).isEqualTo("CADRE");
        assertThat(service.update(actor, 2L, new UpdateUserRequest(
                "Test User", "", Role.JOB_COUTURIERE, "")).workType()).isNull();
        assertThat(user.getRole()).isEqualTo(Role.JOB_COUTURIERE);
    }
}
