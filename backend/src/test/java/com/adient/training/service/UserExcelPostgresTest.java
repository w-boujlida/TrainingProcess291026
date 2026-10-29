package com.adient.training.service;

import com.adient.training.dto.RecruitmentDtos.ImportResult;
import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import com.adient.training.security.JwtUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class UserExcelPostgresTest {
    private static final String PASSWORD = "ImportTest@2026";
    private final String prefix = "ux_" + UUID.randomUUID().toString().substring(0, 8) + "_";
    @Autowired UserExcelService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired UserService userService;

    @Test
    void selectionEndpointsApplyOnlyRequestedRowsAndExportOnlySelection() throws Exception {
        var admin = account(Role.SUPER_ADMIN);
        var first = account(Role.FORMATRICE);
        var second = account(Role.QUALITY);
        String authorization = "Bearer " + jwt.generate(admin);
        String payload = "{\"ids\":[" + first.getId() + "],\"action\":\"WORK_TYPE\",\"workType\":\"INDIRECT\"}";
        mvc.perform(post("/api/users/selection").contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/selection").header("Authorization", authorization)
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workType").value("INDIRECT"));
        assertThat(users.findById(first.getId()).orElseThrow().getWorkType()).isEqualTo("INDIRECT");
        assertThat(users.findById(second.getId()).orElseThrow().getWorkType()).isNull();
        String invalidBatch = "{\"ids\":[" + first.getId() + ",999999999],\"action\":\"DEACTIVATE\"}";
        mvc.perform(post("/api/users/selection").header("Authorization", authorization)
                        .contentType("application/json").content(invalidBatch)).andExpect(status().isNotFound());
        assertThat(users.findById(first.getId()).orElseThrow().isActive()).isTrue();
        mvc.perform(post("/api/users/selection").header("Authorization", "Bearer " + jwt.generate(first))
                        .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        var response = mvc.perform(post("/api/users/excel/export-selection").header("Authorization", authorization)
                        .contentType("application/json").content("[" + first.getId() + "]"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = open(response)) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(first.getUsername());
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("INDIRECT");
            for (Row row : sheet) for (Cell cell : row) assertThat(cell.toString()).doesNotContain(PASSWORD, "$2a$", "$2b$");
        }
    }

    @Test
    void workTypePersistsAndIsIncludedInExports() throws Exception {
        var actor = actor(Role.SUPER_ADMIN);
        String username = prefix + "work_type";
        try (XSSFWorkbook workbook = open(service.template(actor))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Type de travail");
            Row row = sheet.createRow(1);
            String[] values = {username, "Test User", "", "JOB_COUTURIERE", PASSWORD, "Direct"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            var output = new ByteArrayOutputStream();
            workbook.write(output);
            var result = service.importFile(actor, new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.errors()).isEmpty();
            assertThat(users.findByUsernameIgnoreCase(username).orElseThrow().getWorkType()).isEqualTo("DIRECT");
        }
        try (XSSFWorkbook workbook = open(service.export(actor))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Type de travail");
            boolean found = false;
            for (Row row : sheet) {
                if (username.equals(row.getCell(0).getStringCellValue())) {
                    assertThat(row.getCell(8).getStringCellValue()).isEqualTo("DIRECT");
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @AfterEach
    void cleanup() {
        users.deleteAll(users.findAll().stream().filter(user -> user.getUsername().startsWith(prefix)).toList());
    }

    @Test
    void templateContainsRequiredHeadersInstructionsAndOnlyAssignableRoles() throws Exception {
        try (XSSFWorkbook workbook = open(service.template(actor(Role.ADMIN)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Utilisateurs");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Mot de passe temporaire *");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getColumnStyle(4).getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getDataValidations().getFirst().getValidationConstraint().getFormula1())
                    .isEqualTo("AssignableRoles");
            assertThat(workbook.getSheet("_roles")).isNotNull();
            assertThat(workbook.getSheet("_roles").getLastRowNum()).isEqualTo(3);
            assertThat(workbook.getSheet("Instructions")).isNotNull();
        }
        try (XSSFWorkbook workbook = open(service.template(actor(Role.SUPER_ADMIN)))) {
            assertThat(workbook.getSheet("_roles").getRow(0).getCell(0).getStringCellValue()).isEqualTo("SUPER_ADMIN");
            assertThat(workbook.getSheet("_roles").getLastRowNum()).isEqualTo(56);
        }
    }

    @Test
    void createsValidAccountsReportsEachErrorAndNeverUpdatesExistingUsers() throws Exception {
        AuthUser actor = actor(Role.SUPER_ADMIN);
        String username = prefix + "trainer";
        ImportResult result = service.importFile(actor, file(actor, new String[][]{
                {username, "Formatrice test", "trainer@example.test", "Formatrice", PASSWORD},
                {username.toUpperCase(), "Ne pas modifier", "", "ADMIN", PASSWORD},
                {prefix + "weak", "Mot de passe faible", "", "FORMATRICE", "weak"},
                {prefix + "email", "E-mail invalide", "invalide", "QUALITY", PASSWORD},
                {prefix + "unknown", "Rôle inconnu", "", "inconnu", PASSWORD},
                {prefix + "quality", "Qualité test", "", "Qualité", PASSWORD}
        }));
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isEqualTo(4);
        assertThat(result.errors()).extracting(error -> error.row()).containsExactly(3, 4, 5, 6);
        assertThat(result.errors().toString()).doesNotContain(PASSWORD);
        AppUser created = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(created.getFullName()).isEqualTo("Formatrice test");
        assertThat(created.getRole()).isEqualTo(Role.FORMATRICE);
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        ImportResult repeated = service.importFile(actor, file(actor, new String[][]{
                {username.toUpperCase(), "Ne pas modifier", "", "ADMIN", PASSWORD}
        }));
        assertThat(repeated.created()).isZero();
        assertThat(repeated.errors().getFirst().message()).contains("déjà utilisé");
        assertThat(users.findByUsernameIgnoreCase(username).orElseThrow().getFullName()).isEqualTo("Formatrice test");
    }

    @Test
    void adminCannotImportPrivilegedRolesAndLaterRowsStillCommit() throws Exception {
        AuthUser admin = actor(Role.ADMIN);
        ImportResult result = service.importFile(admin, file(admin, new String[][]{
                {prefix + "super", "Super refusé", "", "SUPER_ADMIN", PASSWORD},
                {prefix + "admin", "Admin refusé", "", "ADMIN", PASSWORD},
                {prefix + "leader", "Leader accepté", "", "TEAM_LEADER", PASSWORD}
        }));
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(users.findByUsernameIgnoreCase(prefix + "super")).isEmpty();
        assertThat(users.findByUsernameIgnoreCase(prefix + "admin")).isEmpty();
        assertThat(users.findByUsernameIgnoreCase(prefix + "leader")).isPresent();
    }

    @Test
    void acceptsReorderedHeadersOptionalEmailAndIgnoresBlankRows() throws Exception {
        AuthUser actor = actor(Role.ADMIN);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Utilisateurs");
            String[][] data = {
                    {"Rôle", "Mot de passe temporaire", "Nom complet", "Identifiant"},
                    {"Team Leader", PASSWORD, "Leader test", prefix + "reordered"},
                    {"", "", "", ""}
            };
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < data[i].length; j++) row.createCell(j).setCellValue(data[i][j]);
            }
            ImportResult result = service.importFile(actor, upload(workbook));
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.skipped()).isZero();
        }
    }

    @Test
    void rejectsFormulasAndValidatesIdentifiersWithoutExposingValues() throws Exception {
        AuthUser actor = actor(Role.ADMIN);
        try (XSSFWorkbook workbook = open(service.template(actor))) {
            Row row = workbook.getSheetAt(0).createRow(1);
            String[] values = {prefix + "formula", "Formule", "", "FORMATRICE", PASSWORD};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            row.getCell(4).setCellFormula("\"" + PASSWORD + "\"");
            ImportResult result = service.importFile(actor, upload(workbook));
            assertThat(result.created()).isZero();
            assertThat(result.errors().getFirst().message()).contains("formules").doesNotContain(PASSWORD);
        }
        ImportResult invalid = service.importFile(actor, file(actor, new String[][]{
                {"bad user name", "Nom valide", "", "FORMATRICE", PASSWORD}
        }));
        assertThat(invalid.skipped()).isEqualTo(1);
        assertThat(invalid.errors().getFirst().message()).contains("identifiant");
    }

    @Test
    void rejectsMalformedFilesMissingHeadersAndExcessRowsBeforeCreatingAnything() throws Exception {
        AuthUser actor = actor(Role.ADMIN);
        assertThatThrownBy(() -> service.importFile(actor, new MockMultipartFile("file", "bad.xlsx", null, new byte[]{1, 2, 3})))
                .isInstanceOf(ApiException.class).hasMessageContaining("Impossible de lire");
        assertThatThrownBy(() -> service.importFile(actor, new MockMultipartFile("file", "old.xls", null, new byte[]{1})))
                .isInstanceOf(ApiException.class).hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> service.importFile(actor, new MockMultipartFile("file", "empty.xlsx", null, new byte[0])))
                .isInstanceOf(ApiException.class).hasMessageContaining("Aucun fichier");
        try (XSSFWorkbook workbook = open(service.template(actor))) {
            workbook.getSheetAt(0).getRow(0).removeCell(workbook.getSheetAt(0).getRow(0).getCell(4));
            assertThatThrownBy(() -> service.importFile(actor, upload(workbook)))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Colonne manquante");
        }
        try (XSSFWorkbook workbook = open(service.template(actor))) {
            workbook.getSheetAt(0).createRow(5001).createCell(0).setCellValue("trop");
            assertThatThrownBy(() -> service.importFile(actor, upload(workbook)))
                    .isInstanceOf(ApiException.class).hasMessageContaining("5000");
        }
    }

    @Test
    void exportContainsAllAccountsButNoPasswordOrFormula() throws Exception {
        AuthUser actor = actor(Role.SUPER_ADMIN);
        service.importFile(actor, file(actor, new String[][]{
                {prefix + "export", "=1+1", "export@example.test", "QUALITY", PASSWORD}
        }));
        try (XSSFWorkbook workbook = open(service.export(actor))) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean found = false;
            for (Row row : sheet) {
                for (Cell cell : row) {
                    assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
                    assertThat(cell.toString()).doesNotContain(PASSWORD, "$2a$", "$2b$");
                }
                if (row.getCell(0).toString().equals(prefix + "export")) {
                    found = true;
                    assertThat(row.getCell(1).getStringCellValue()).isEqualTo("=1+1");
                    assertThat(row.getCell(4).getStringCellValue()).isEqualTo("Actif");
                }
            }
            assertThat(found).isTrue();
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 9);
        }
    }

    @Test
    void endpointsEnforceAuthenticationRolesAndPasswordChangeRequirement() throws Exception {
        mvc.perform(get("/api/users/excel/template")).andExpect(status().isUnauthorized());
        AppUser trainer = account(Role.FORMATRICE);
        for (String endpoint : new String[]{"template", "export"}) {
            mvc.perform(get("/api/users/excel/" + endpoint).header("Authorization", "Bearer " + jwt.generate(trainer)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/users/excel/import")
                        .file(file(actor(Role.ADMIN), new String[0][]))
                        .header("Authorization", "Bearer " + jwt.generate(trainer)))
                .andExpect(status().isForbidden());
        AppUser admin = account(Role.ADMIN);
        String token = jwt.generate(admin);
        mvc.perform(get("/api/users/excel/template").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"modele_import_utilisateurs.xlsx\""));
        mvc.perform(multipart("/api/users/excel/import")
                        .file(file(new AuthUser(admin.getId(), admin.getUsername(), admin.getRole()), new String[][]{
                                {prefix + "api", "Compte API", "", "SUPERVISEUR", PASSWORD}
                        })).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0));
        AppUser imported = users.findByUsernameIgnoreCase(prefix + "api").orElseThrow();
        mvc.perform(get("/api/users/excel/export").header("Authorization", "Bearer " + jwt.generate(imported)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/users/excel/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nonManagersCannotCallExcelServiceDirectly() {
        AuthUser trainer = actor(Role.FORMATRICE);
        assertThatThrownBy(() -> service.template(trainer)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.export(trainer)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.importFile(trainer, null)).isInstanceOf(ApiException.class);
    }

    private AuthUser actor(Role role) { return new AuthUser(0L, "test-actor", role); }

    private AppUser account(Role role) {
        AppUser account = new AppUser();
        account.setUsername(prefix + role.name().toLowerCase());
        account.setFullName("Test Excel " + role.name());
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setRole(role);
        account.setMustChangePassword(false);
        return users.saveAndFlush(account);
    }

    private MockMultipartFile file(AuthUser actor, String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = open(service.template(actor))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) row.createCell(j).setCellValue(rows[i][j]);
            }
            return upload(workbook);
        }
    }

    private MockMultipartFile upload(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
    }

    private XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }
}
