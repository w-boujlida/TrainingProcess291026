package com.adient.training.security;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.repository.UserRepository;
import com.adient.training.service.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobRoleAccessTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void jobRolesCannotReadOrWriteAnyBusinessModule() throws Exception {
        for (String path : new String[]{"/api/recruitment", "/api/training-sheets",
                "/api/users", "/api/audit-events", "/api/anything"}) {
            for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
                var response = request(Role.JOB_COUTURIERE, path, method, false);
                assertThat(response.getStatus()).isEqualTo(403);
                assertThat(response.getContentAsString()).contains("MODULE_ACCESS_DENIED");
            }
        }
    }

    @Test void jobsKeepAccountEndpointsAndLegacyRolesKeepModuleAccess() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/auth/change-password",
                "/api/auth/logout", "/api/i18n"}) {
            assertThat(request(Role.JOB_COUTURIERE, path, "POST", false).getStatus()).isEqualTo(204);
            assertThat(request(Role.JOB_COUTURIERE, path, "POST", true).getStatus()).isEqualTo(204);
        }
        assertThat(request(Role.FORMATRICE, "/api/recruitment", "GET", false).getStatus()).isEqualTo(204);
        assertThat(request(Role.JOB_COUTURIERE, "/api/recruitment", "GET", true)
                .getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    private MockHttpServletResponse request(Role role, String path, String method, boolean changePassword) throws Exception {
        SecurityContextHolder.clearContext();
        var jwt = mock(JwtUtil.class);
        var users = mock(UserRepository.class);
        var user = new AppUser();
        user.setRole(role);
        user.setUsername("12");
        user.setMustChangePassword(changePassword);
        when(jwt.parse("test")).thenReturn(Optional.of(Jwts.claims().subject("1").build()));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var filter = new JwtAuthFilter(jwt, users, new I18nService(), new ObjectMapper(),
                new AcceptHeaderLocaleResolver());
        var req = new MockHttpServletRequest(method, path);
        req.addHeader("Authorization", "Bearer test");
        var res = new MockHttpServletResponse();
        var continued = new AtomicBoolean();
        filter.doFilter(req, res, (request, response) -> { continued.set(true); res.setStatus(204); });
        assertThat(continued.get()).isEqualTo(res.getStatus() == 204);
        return res;
    }
}
