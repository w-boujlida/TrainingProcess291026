package com.adient.training.security;

import com.adient.training.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditFilterTest {
    @Test
    void hrPreviewAndImportAreAuditedWithoutPasswordsOrFieldValues() throws Exception {
        for (String endpoint : new String[]{"preview", "apply"}) {
            var service = mock(AuditService.class);
            var request = new MockHttpServletRequest("POST", "/api/users/hr-import/" + endpoint);
            request.setContent("{\"password\":\"PRIVATE\",\"fields\":\"PRIVATE\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            new AuditFilter(service, "").doFilter(request, response, (req, res) -> {
                res.getWriter().write("{\"temporaryPassword\":\"PRIVATE\"}");
            });
            var event = ArgumentCaptor.forClass(AuditService.Event.class);
            verify(service).record(event.capture());
            assertThat(event.getValue().resource()).isEqualTo("/api/users/hr-import/" + endpoint);
            assertThat(event.getValue().action()).isEqualTo(endpoint.equals("preview") ? "CONSULTATION" : "IMPORT_EXCEL");
            assertThat(event.getValue().toString()).doesNotContain("PRIVATE");
        }
    }
    @Test
    void auditFailureIsReportedWithoutDisguisingTheAlreadyCompletedAction() throws Exception {
        AuditService service = mock(AuditService.class);
        doThrow(new DataAccessResourceFailureException("Audit database unavailable")).when(service).record(any());
        var request = new MockHttpServletRequest("POST", "/api/users");
        var response = new MockHttpServletResponse();
        new AuditFilter(service, "").doFilter(request, response, (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":42}");
        });
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":42}");
        assertThat(response.getHeader("X-Audit-Status")).isEqualTo("failed");
    }

    @Test
    void unknownRoutesNeverStoreArbitrarySensitivePathsAndErrorsStayErrors() {
        AuditService service = mock(AuditService.class);
        var request = new MockHttpServletRequest("GET", "/api/secret-token-value");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new AuditFilter(service, "").doFilter(request, response, (req, res) -> {
            throw new jakarta.servlet.ServletException("Unexpected failure");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        var event = ArgumentCaptor.forClass(AuditService.Event.class);
        verify(service).record(event.capture());
        assertThat(event.getValue().resource()).isEqualTo("/api/other");
        assertThat(event.getValue().status()).isEqualTo(500);
    }
}
