package com.adient.training.service;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.audit.trusted-proxy-host=localhost")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class AuditPostgresTest {
    private static final String PASSWORD = "AuditTest@2026";
    private final String prefix = "audit_" + UUID.randomUUID().toString().substring(0, 8);
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_event WHERE username LIKE ?", prefix + "%");
        users.deleteAll(users.findAll().stream().filter(user -> user.getUsername().startsWith(prefix)).toList());
    }

    @Test
    void successfulAndFailedLoginsHaveIdentityIpDeviceAndNoCredentials() throws Exception {
        AppUser account = account(Role.FORMATRICE);
        String body = json.writeValueAsString(Map.of("username", account.getUsername(), "password", PASSWORD, "deviceName", "PC-Formation-01"));
        String response = mvc.perform(post("/api/auth/login").contentType("application/json").content(body)
                        .header("User-Agent", "Mozilla/5.0 Windows Chrome/130.0")
                        .header("X-Real-IP", "192.0.2.15"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/recruitment/candidates").header("Authorization", "Bearer " + token)
                        .header("X-Real-IP", "192.0.2.15"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", account.getUsername(), "password", "Wrong@2026", "deviceName", "PC-Tentative"))))
                .andExpect(status().isUnauthorized());
        List<AuditService.Entry> entries = events(account);
        assertThat(entries).hasSize(3);
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("CONNEXION");
            assertThat(entry.status()).isEqualTo(200);
            assertThat(entry.actorId()).isEqualTo(account.getId());
            assertThat(entry.ip()).isEqualTo("192.0.2.15");
            assertThat(entry.ipSource()).isEqualTo("PROXY");
            assertThat(entry.deviceName()).isEqualTo("PC-Formation-01");
        });
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("CONSULTATION");
            assertThat(entry.deviceName()).isEqualTo("PC-Formation-01");
        });
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("CONNEXION");
            assertThat(entry.status()).isEqualTo(401);
            assertThat(entry.deviceName()).isEqualTo("PC-Tentative");
        });
        assertThat(json.writeValueAsString(entries)).doesNotContain(PASSWORD, "Wrong@2026", token, account.getPasswordHash());
    }

    @Test
    void unknownUserLockedAndInactiveAttemptsAreRecorded() throws Exception {
        AppUser inactive = account(Role.ADMIN);
        inactive.setActive(false);
        users.saveAndFlush(inactive);
        AppUser locked = account(Role.QUALITY);
        locked.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        users.saveAndFlush(locked);
        for (String username : new String[]{prefix + "_missing", inactive.getUsername(), locked.getUsername()}) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403, 423));
            var entries = audit.search(username, null, "CONNEXION", "FAILURE", null, null, 0, 50).items();
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void failedLoginWithAnExistingBearerStillTracksTheAttemptedUsername() throws Exception {
        AppUser actor = account(Role.SUPER_ADMIN);
        String attempted = prefix + "_attempted";
        mvc.perform(post("/api/auth/login").header("Authorization", bearer(actor)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", attempted, "password", PASSWORD, "deviceName", "PC-Tentative"))))
                .andExpect(status().isUnauthorized());
        var entries = audit.search(attempted, null, "CONNEXION", null, null, null, 0, 50).items();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().actorId()).isNull();
        assertThat(events(actor)).isEmpty();
    }

    @Test
    void permissionsAndValidationCannotBeBypassed() throws Exception {
        mvc.perform(get("/api/audit-events")).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.ADMIN, Role.FORMATRICE, Role.SUPERVISEUR, Role.TEAM_LEADER, Role.QUALITY}) {
            AppUser account = account(role);
            mvc.perform(get("/api/audit-events").header("Authorization", bearer(account))).andExpect(status().isForbidden());
            assertThat(events(account)).anyMatch(entry -> entry.resource().equals("/api/audit-events") && entry.status() == 403);
        }
        AppUser superAdmin = account(Role.SUPER_ADMIN);
        mvc.perform(get("/api/audit-events").header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        for (String query : new String[]{"size=101", "page=-1", "outcome=INVALID",
                "from=2026-10-26T00:00:00Z&to=2026-10-25T00:00:00Z"}) {
            mvc.perform(get("/api/audit-events?" + query).header("Authorization", bearer(superAdmin)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createsUpdatesDeletesExportsImportsAndLogoutAreTracedWithoutPayloads() throws Exception {
        AppUser admin = account(Role.SUPER_ADMIN);
        String targetUsername = prefix + "_target";
        String created = mvc.perform(post("/api/users").header("Authorization", bearer(admin)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", targetUsername, "fullName", "Nom confidentiel",
                                "email", "private@example.test", "role", "FORMATRICE", "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        mvc.perform(put("/api/users/" + id).header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"fullName\":\"Nom modifié\",\"role\":\"QUALITY\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/users/excel/export").header("Authorization", bearer(admin))).andExpect(status().isOk());
        byte[] template = mvc.perform(get("/api/users/excel/template").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/api/users/excel/import").file(new MockMultipartFile("file", "users.xlsx", null, template))
                .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(delete("/api/users/" + id).header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        var entries = events(admin);
        assertThat(entries).extracting(AuditService.Entry::action).contains(
                "CREATION", "MODIFICATION", "SUPPRESSION", "EXPORT_EXCEL", "MODELE_EXCEL", "IMPORT_EXCEL", "DECONNEXION");
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("IMPORT_EXCEL");
            assertThat(entry.affectedCount()).isZero();
            assertThat(entry.rejectedCount()).isZero();
        });
        assertThat(json.writeValueAsString(entries)).doesNotContain(PASSWORD, "Nom confidentiel", "private@example.test");
    }

    @Test
    void signaturesAndFailedActionsAreRecordedWithoutDrawingOrQueryStrings() throws Exception {
        AppUser admin = account(Role.SUPER_ADMIN);
        String drawing = "data:image/png;base64,private-signature";
        mvc.perform(post("/api/recruitment/candidates/999999999/booklet/candidate-signature?secret=private-query")
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("signature", drawing))))
                .andExpect(status().isNotFound());
        assertThat(events(admin)).anyMatch(entry -> entry.action().equals("SIGNATURE") && entry.status() == 404);
        assertThat(json.writeValueAsString(events(admin))).doesNotContain(drawing, "private-query", "secret");
    }

    @Test
    void clientCannotForgeForwardedIpFromAnUntrustedPeer() throws Exception {
        AppUser admin = account(Role.SUPER_ADMIN);
        mvc.perform(get("/api/users").header("Authorization", bearer(admin))
                        .header("X-Real-IP", "203.0.113.15").header("X-Forwarded-For", "203.0.113.16")
                        .with(request -> { request.setRemoteAddr("198.51.100.10"); return request; }))
                .andExpect(status().isOk());
        var event = events(admin).getFirst();
        assertThat(event.ip()).isEqualTo("198.51.100.10");
        assertThat(event.peer()).isEqualTo("198.51.100.10");
        assertThat(event.ipSource()).isEqualTo("PEER");
    }

    @Test
    void filteringPaginationAndNinetyDayRetentionWork() {
        for (int i = 0; i < 4; i++) {
            audit.record(new AuditService.Event(null, prefix, null, "CONSULTATION", "/api/users", "GET",
                    i == 0 ? 403 : 200, "192.0.2.1", "127.0.0.1", "PEER", "Test", "PC-test", null, null));
        }
        assertThat(audit.search(prefix, "192.0.2.1", "CONSULTATION", "SUCCESS", null, null, 0, 2).total()).isEqualTo(3);
        var first = audit.search(prefix, null, null, null, null, null, 0, 2);
        var second = audit.search(prefix, null, null, null, null, null, 1, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(first.items()).extracting(AuditService.Entry::id)
                .doesNotContainAnyElementsOf(second.items().stream().map(AuditService.Entry::id).toList());
        assertThat(audit.search(prefix, null, null, null, Instant.now().plusSeconds(60), null, 0, 50).total()).isZero();
        jdbc.update("UPDATE audit_event SET occurred_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(91, ChronoUnit.DAYS)), first.items().getFirst().id());
        assertThat(audit.search(prefix, null, null, null, null, null, 0, 50).total()).isEqualTo(3);
        audit.purgeExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE username = ?", Long.class, prefix)).isEqualTo(3);
    }

    @Test
    void oldSessionsWithoutDeviceStillWorkAndSnapshotsSurviveUserDeletion() throws Exception {
        AppUser trainer = account(Role.FORMATRICE);
        mvc.perform(get("/api/recruitment/candidates").header("Authorization", bearer(trainer))).andExpect(status().isOk());
        Long id = trainer.getId();
        users.deleteById(id);
        var stored = events(trainer).getFirst();
        assertThat(stored.actorId()).isEqualTo(id);
        assertThat(stored.deviceName()).isNull();
        assertThat(stored.username()).isEqualTo(trainer.getUsername());
    }

    private List<AuditService.Entry> events(AppUser account) {
        return audit.search(account.getUsername(), null, null, null, null, null, 0, 100).items();
    }

    private AppUser account(Role role) {
        AppUser user = new AppUser();
        user.setUsername(prefix + "_" + role.name().toLowerCase());
        user.setFullName("Test audit");
        user.setRole(role);
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setMustChangePassword(false);
        return users.saveAndFlush(user);
    }

    private String bearer(AppUser account) { return "Bearer " + jwt.generate(account); }
}
