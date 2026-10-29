package com.adient.training.service;

import com.adient.training.dto.Dtos.UserSelectionRequest;
import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class UserSelectionTest {
    private final UserRepository repo = mock(UserRepository.class);
    private final UserService service = new UserService(repo, mock(PasswordEncoder.class), mock(PasswordPolicy.class));
    private final AuthUser actor = new AuthUser(1L, "admin", Role.SUPER_ADMIN);

    private AppUser user(long id, Role role) {
        var user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername("test" + id);
        user.setFullName("Test");
        user.setRole(role);
        when(repo.findById(id)).thenReturn(Optional.of(user));
        when(repo.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        return user;
    }

    @Test void changesOnlySelectedWorkTypesAndDeduplicatesIds() {
        var first = user(2L, Role.JOB_COUTURIERE);
        var other = user(3L, Role.JOB_COUTURIERE);
        var result = service.applySelection(actor, new UserSelectionRequest(List.of(2L, 2L), "WORK_TYPE", "DIRECT"));
        assertThat(result).hasSize(1);
        assertThat(first.getWorkType()).isEqualTo("DIRECT");
        assertThat(other.getWorkType()).isNull();
        assertThat(first.getRole()).isEqualTo(Role.JOB_COUTURIERE);
    }

    @Test void validatesWholeSelectionBeforeAnyMutation() {
        var first = user(2L, Role.JOB_COUTURIERE);
        assertThatThrownBy(() -> service.applySelection(actor,
                new UserSelectionRequest(List.of(2L, 99L), "DEACTIVATE", null))).isInstanceOf(RuntimeException.class);
        assertThat(first.isActive()).isTrue();
        assertThatThrownBy(() -> service.applySelection(actor,
                new UserSelectionRequest(List.of(2L, 1L), "DEACTIVATE", null))).isInstanceOf(RuntimeException.class);
        assertThat(first.isActive()).isTrue();
        assertThatThrownBy(() -> service.applySelection(new AuthUser(1L, "admin", Role.ADMIN),
                new UserSelectionRequest(List.of(2L), "WORK_TYPE", "CADRE"))).isInstanceOf(RuntimeException.class);
        verify(repo, never()).saveAll(anyList());
    }

    @Test void protectsLastActiveSuperAdminsAndAllowsUnlocking() {
        user(2L, Role.SUPER_ADMIN);
        user(3L, Role.SUPER_ADMIN);
        when(repo.countByRoleAndActiveTrue(Role.SUPER_ADMIN)).thenReturn(2L);
        assertThatThrownBy(() -> service.applySelection(actor,
                new UserSelectionRequest(List.of(2L, 3L), "DEACTIVATE", null))).isInstanceOf(RuntimeException.class);
        var user = user(4L, Role.FORMATRICE);
        user.setActive(false);
        user.setFailedAttempts(4);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        service.applySelection(actor, new UserSelectionRequest(List.of(4L), "ACTIVATE", null));
        assertThat(user.isActive()).isTrue();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        service.applySelection(actor, new UserSelectionRequest(List.of(4L), "DEACTIVATE", null));
        assertThat(user.isActive()).isFalse();
    }

    @Test void rejectsEmptyInvalidActionsAndTypes() {
        for (var request : List.of(
                new UserSelectionRequest(List.of(), "ACTIVATE", null),
                new UserSelectionRequest(List.of(2L), "DELETE", null),
                new UserSelectionRequest(List.of(2L), "WORK_TYPE", null),
                new UserSelectionRequest(List.of(2L), "WORK_TYPE", "other"))) {
            assertThatThrownBy(() -> service.applySelection(actor, request)).isInstanceOf(RuntimeException.class);
        }
        verifyNoInteractions(repo);
    }

    @Test void selectionSupportsMoreThanFiveThousandButHasAnExplicitBound() {
        var target = user(2L, Role.JOB_EMPLOYEE);
        assertThat(service.applySelection(actor, new UserSelectionRequest(
                java.util.Collections.nCopies(5055, 2L), "WORK_TYPE", "DIRECT"))).hasSize(1);
        assertThat(target.getWorkType()).isEqualTo("DIRECT");
        assertThatThrownBy(() -> service.applySelection(actor, new UserSelectionRequest(
                java.util.Collections.nCopies(10001, 2L), "WORK_TYPE", "DIRECT"))).isInstanceOf(RuntimeException.class);
    }
}
