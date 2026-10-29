package com.adient.training.service;

import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.entity.Role;
import com.adient.training.repository.CandidateRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import com.adient.training.security.JwtUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class LocalizationPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired I18nService i18n;
    @Autowired UserExcelService userExcel;
    @Autowired CandidateExcelService candidateExcel;
    @Autowired UserRepository users;
    @Autowired CandidateRepository candidates;
    @Autowired JwtUtil jwt;
    private final String username = "lang_" + UUID.randomUUID().toString().substring(0, 8);
    private final String cin = UUID.randomUUID().toString().substring(0, 8);
    private final AuthUser actor = new AuthUser(1L, "localization-test", Role.SUPER_ADMIN);

    @AfterEach
    void cleanup() {
        LocaleContextHolder.resetLocaleContext();
        users.findByUsernameIgnoreCase(username).ifPresent(users::delete);
        candidates.findByCinIgnoreCase(cin).ifPresent(candidates::delete);
    }

    @Test
    void catalogsArePublicEvenWhenNoSessionExists() throws Exception {
        mvc.perform(get("/api/i18n")).andExpect(status().isOk())
                .andExpect(jsonPath("$.en.Langue").value("Language"))
                .andExpect(jsonPath("$.it.Langue").value("Lingua"))
                .andExpect(jsonPath("$.ar.Langue").value("اللغة"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "en", "it", "ar"})
    void validationUsesRequestedLanguage(String language) throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", language)
                        .contentType("application/json").content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        i18n.translate("L'identifiant est obligatoire.", Locale.forLanguageTag(language)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "en", "it", "ar"})
    void usersTemplateImportsInEveryLanguageAndKeepsPersonalData(String language) throws Exception {
        Locale locale = Locale.forLanguageTag(language);
        LocaleContextHolder.setLocale(locale);
        byte[] file;
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(userExcel.template(actor)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo(i18n.translate("Identifiant *"));
            assertThat(sheet.isRightToLeft()).isEqualTo(language.equals("ar"));
            var row = sheet.createRow(1);
            String[] values = {username, "Formation", "nom@example.test", "FORMATRICE", "Localization@Test26"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            var output = new ByteArrayOutputStream();
            workbook.write(output);
            file = output.toByteArray();
        }
        LocaleContextHolder.setLocale(Locale.FRENCH);
        var result = userExcel.importFile(actor, new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file));
        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).isOne();
        var account = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(account.getFullName()).isEqualTo("Formation");
        String bearer = "Bearer " + jwt.generate(account);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer).header("Accept-Language", language))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Formation"))
                .andExpect(jsonPath("$.role").value("FORMATRICE"));
        mvc.perform(get("/api/recruitment/candidates").header("Authorization", bearer)
                        .header("Accept-Language", language))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.message").value(i18n.translate("Vous devez changer votre mot de passe.", locale)));
        mvc.perform(get("/api/i18n").header("Authorization", bearer).header("Accept-Language", language))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en.Langue").value("Language"));
        account.setRole(Role.SUPER_ADMIN);
        account.setMustChangePassword(false);
        users.saveAndFlush(account);
        String managerBearer = "Bearer " + jwt.generate(account);
        mvc.perform(multipart("/api/users/excel/import").file(new MockMultipartFile("file", "users.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file))
                        .header("Authorization", managerBearer).header("Accept-Language", language))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.errors[0].message").value(i18n.translate("Cet identifiant est déjà utilisé.", locale)));
        LocaleContextHolder.setLocale(locale);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(userExcel.export(actor)))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo(i18n.translate("Identifiant"));
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "en", "it", "ar"})
    void candidateTemplateImportsTranslatedEnumsAndPreservesCanonicalValues(String language) throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        byte[] file;
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(candidateExcel.template(RecruitmentCategory.DIRECT)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo(i18n.translate("Nom et prénom *"));
            assertThat(sheet.isRightToLeft()).isEqualTo(language.equals("ar"));
            var row = sheet.createRow(1);
            row.createCell(1).setCellValue("Formation");
            row.createCell(2).setCellValue(cin);
            row.createCell(7).setCellValue(i18n.translate("Préparation"));
            row.createCell(11).setCellValue(i18n.translate("Matin"));
            row.createCell(14).setCellValue(i18n.translate("Candidat"));
            var output = new ByteArrayOutputStream();
            workbook.write(output);
            file = output.toByteArray();
        }
        LocaleContextHolder.setLocale(Locale.FRENCH);
        var result = candidateExcel.importFile(actor, RecruitmentCategory.DIRECT, new MockMultipartFile(
                "file", "candidates.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file));
        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).isOne();
        var candidate = candidates.findByCinIgnoreCase(cin).orElseThrow();
        assertThat(candidate.getFullName()).isEqualTo("Formation");
        assertThat(candidate.getPosition()).isEqualTo("Préparation");
        assertThat(candidate.getShift().name()).isEqualTo("MATIN");
    }
}
