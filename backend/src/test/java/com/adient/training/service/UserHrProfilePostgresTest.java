package com.adient.training.service;

import com.adient.training.entity.*;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class UserHrProfilePostgresTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    private AppUser account(Role role) {
        var user = new AppUser();
        user.setUsername("hr-test-" + UUID.randomUUID().toString().substring(0, 20));
        user.setFullName("Synthetic HR test");
        user.setPasswordHash("unused");
        user.setRole(role);
        user.setMustChangePassword(false);
        return users.saveAndFlush(user);
    }
    private String bearer(AppUser user) { return "Bearer " + jwt.generate(user, null); }
    private long profile() {
        long id = account(Role.JOB_EMPLOYEE).getId();
        db.update("INSERT INTO user_hr_profile(user_id,source_file,sheet_name,fields) VALUES (?,?,'RH',?::jsonb)",
                id, "synthetic.xlsx", "[{\"column\":\"AU\",\"label\":\"COUT/H\",\"value\":\"PRIVATE-HR-TEST\"}]");
        return id;
    }
    @Test void onlySuperAdminCanReadPrivateProfileWithNoCache() throws Exception {
        long id = profile();
        mvc.perform(get("/api/users/" + id + "/hr-profile")).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.ADMIN, Role.FORMATRICE, Role.TEAM_LEADER, Role.SUPERVISEUR, Role.QUALITY, Role.JOB_EMPLOYEE}) {
            mvc.perform(get("/api/users/" + id + "/hr-profile").header("Authorization", bearer(account(role))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/users/" + id + "/hr-profile").header("Authorization", bearer(account(Role.SUPER_ADMIN))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fields[0].value").value("PRIVATE-HR-TEST"));
    }
    @Test void listsAndOrdinaryExportsNeverIncludePrivateFields() throws Exception {
        profile();
        var admin = account(Role.ADMIN);
        String list = mvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("PRIVATE-HR-TEST", "COUT/H", "sourceFile", "fields");
        var bytes = mvc.perform(get("/api/users/excel/export").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 9);
            for (var row : sheet) for (var cell : row) assertThat(cell.toString()).doesNotContain("PRIVATE-HR-TEST", "COUT/H");
        }
    }
    @Test void missingProfileIsExplicitAndNewRoleHasNoModuleAccess() throws Exception {
        long id = account(Role.JOB_EMPLOYEE).getId();
        mvc.perform(get("/api/users/" + id + "/hr-profile").header("Authorization", bearer(account(Role.SUPER_ADMIN))))
                .andExpect(status().isNotFound());
        assertThat(Role.JOB_EMPLOYEE.hasModuleAccess()).isFalse();
    }
}
