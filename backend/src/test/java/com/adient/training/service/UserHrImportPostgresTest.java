package com.adient.training.service;

import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.adient.training.service.HrWorkbookReaderTest.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class UserHrImportPostgresTest {
    @Autowired UserHrImportService service;
    @Autowired UserHrProfileService profiles;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper mapper;
    private final AuthUser actor = new AuthUser(1L, "superadmin", Role.SUPER_ADMIN);
    private AppUser account(Role role) {
        var user = new AppUser();
        user.setUsername("hr-" + UUID.randomUUID().toString().substring(0, 20));
        user.setFullName("Existing test");
        user.setRole(role);
        user.setWorkType("INDIRECT");
        user.setPasswordHash("existing-hash");
        user.setMustChangePassword(false);
        return users.saveAndFlush(user);
    }
    @Test void previewDoesNotWriteAndApplyUpdatesExistingAndCreatesRecruitWithPassword() throws Exception {
        var old = account(Role.JOB_COUTURIERE);
        String fresh = "new-" + UUID.randomUUID().toString().substring(0, 20);
        var file = workbook(sheet -> {
            employee(sheet, 1, old.getUsername(), "Updated test");
            employee(sheet, 2, fresh, "New test");
        });
        long count = users.count();
        var preview = service.preview(actor, file);
        assertThat(users.count()).isEqualTo(count);
        assertThat(preview.updates()).isEqualTo(1);
        assertThat(preview.creates()).isEqualTo(1);
        assertThat(preview.newRecruits()).allMatch(r -> r.temporaryPassword() == null);
        var result = service.apply(actor, file, preview.confirmation());
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        var recruit = result.newRecruits().getFirst();
        var created = users.findByUsernameIgnoreCase(fresh).orElseThrow();
        assertThat(encoder.matches(recruit.temporaryPassword(), created.getPasswordHash())).isTrue();
        assertThat(created.getRole()).isEqualTo(Role.JOB_EMPLOYEE);
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, old.getId())).isEqualTo("existing-hash");
        assertThat(db.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, old.getId())).isEqualTo(Role.JOB_COUTURIERE.name());
        assertThat(profiles.get(actor, old.getId()).fields()).hasSize(62);
        assertThatThrownBy(() -> service.apply(actor, file, preview.confirmation())).isInstanceOf(ApiException.class);
    }
    @Test void emptyCellsPreserveProfileAndAccountValuesAndAbsentUsersAreUntouched() throws Exception {
        var old = account(Role.JOB_EMPLOYEE);
        var absent = account(Role.JOB_EMPLOYEE);
        var initial = workbook(sheet -> employee(sheet, 1, old.getUsername(), "Saved name").createCell(5).setCellValue("Saved department"));
        service.apply(actor, initial, service.preview(actor, initial).confirmation());
        var blank = workbook(sheet -> {
            var row = employee(sheet, 1, old.getUsername(), "");
            row.getCell(0).setBlank(); row.getCell(10).setBlank();
        });
        var p = service.preview(actor, blank);
        service.apply(actor, blank, p.confirmation());
        assertThat(profiles.get(actor, old.getId()).fields()).anyMatch(f -> f.column().equals("F") && f.value().equals("Saved department"));
        assertThat(db.queryForObject("SELECT full_name FROM app_user WHERE id=?", String.class, old.getId())).isEqualTo("Saved name");
        assertThat(db.queryForObject("SELECT full_name FROM app_user WHERE id=?", String.class, absent.getId())).isEqualTo("Existing test");
    }
    @Test void protectedAccountsAreSkippedAndInactiveNewAccountsStayInactive() throws Exception {
        var admin = account(Role.ADMIN);
        String fresh = "new-" + UUID.randomUUID().toString().substring(0, 20);
        var file = workbook(sheet -> {
            employee(sheet, 1, admin.getUsername(), "DO NOT UPDATE");
            employee(sheet, 2, fresh, "Inactive new").getCell(0).setCellValue("Non-Actif");
        });
        var p = service.preview(actor, file);
        assertThat(p.protectedAccounts()).isEqualTo(1);
        var result = service.apply(actor, file, p.confirmation());
        assertThat(result.newRecruits().getFirst().active()).isFalse();
        assertThat(users.findByUsernameIgnoreCase(fresh).orElseThrow().isActive()).isFalse();
        assertThat(db.queryForObject("SELECT full_name FROM app_user WHERE id=?", String.class, admin.getId())).isEqualTo("Existing test");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_hr_profile WHERE user_id=?", Integer.class, admin.getId())).isZero();
    }
    @Test void staleFileOrDatabaseRejectsConfirmation() throws Exception {
        var old = account(Role.JOB_EMPLOYEE);
        var file = workbook(sheet -> employee(sheet, 1, old.getUsername(), "Updated"));
        var p = service.preview(actor, file);
        var changed = workbook(sheet -> employee(sheet, 1, old.getUsername(), "Different file"));
        assertThatThrownBy(() -> service.apply(actor, changed, p.confirmation())).isInstanceOf(ApiException.class);
        db.update("UPDATE app_user SET full_name='Concurrent' WHERE id=?", old.getId());
        assertThatThrownBy(() -> service.apply(actor, file, p.confirmation())).isInstanceOf(ApiException.class);
        assertThat(db.queryForObject("SELECT full_name FROM app_user WHERE id=?", String.class, old.getId())).isEqualTo("Concurrent");
    }
    @Test void invalidRowsNeverPartiallyApply() throws Exception {
        var old = account(Role.JOB_EMPLOYEE);
        var file = workbook(sheet -> {
            employee(sheet, 1, old.getUsername(), "Do not update");
            employee(sheet, 2, old.getUsername(), "Duplicate");
        });
        var p = service.preview(actor, file);
        assertThat(p.errors()).isNotEmpty();
        assertThatThrownBy(() -> service.apply(actor, file, p.confirmation())).isInstanceOf(ApiException.class);
        assertThat(db.queryForObject("SELECT full_name FROM app_user WHERE id=?", String.class, old.getId())).isEqualTo("Existing test");
    }
    @Test void endpointsRequireSuperAdminAndResponsesAreNotCached() throws Exception {
        var file = workbook(sheet -> employee(sheet, 1, "TEST_NEW", "Synthetic"));
        for (Role role : new Role[]{Role.ADMIN, Role.FORMATRICE, Role.JOB_EMPLOYEE}) {
            var user = account(role);
            for (String endpoint : new String[]{"preview", "apply"}) {
                mvc.perform(multipart("/api/users/hr-import/" + endpoint).file(file).param("confirmation", "wrong")
                        .header("Authorization", "Bearer " + jwt.generate(user, null))).andExpect(status().isForbidden());
            }
        }
        var admin = account(Role.SUPER_ADMIN);
        var response = mvc.perform(multipart("/api/users/hr-import/preview").file(file)
                .header("Authorization", "Bearer " + jwt.generate(admin, null)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.creates").value(1)).andReturn();
        String confirmation = mapper.readTree(response.getResponse().getContentAsString()).get("confirmation").asText();
        mvc.perform(multipart("/api/users/hr-import/apply").file(file).param("confirmation", confirmation)
                .header("Authorization", "Bearer " + jwt.generate(admin, null)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.newRecruits[0].temporaryPassword").isNotEmpty());
        mvc.perform(multipart("/api/users/hr-import/preview").file(file)).andExpect(status().isUnauthorized());
    }
}
