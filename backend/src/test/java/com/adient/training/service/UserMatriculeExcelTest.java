package com.adient.training.service;

import com.adient.training.dto.Dtos.CreateUserRequest;
import com.adient.training.entity.Role;
import com.adient.training.security.AuthUser;
import jakarta.validation.Validation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserMatriculeExcelTest {
    @Test
    void acceptsNumericMatriculeAndLegacyIdentifierHeaders() throws Exception {
        var users = mock(UserService.class);
        var actor = new AuthUser(1L, "admin", Role.SUPER_ADMIN);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new UserExcelService(users, factory.getValidator(), new I18nService());
            for (String header : new String[]{"Matricule", "Matricule *", "Identifiant *"}) {
                try (var workbook = new XSSFWorkbook()) {
                    var sheet = workbook.createSheet("Utilisateurs");
                    var titles = sheet.createRow(0);
                    String[] headers = {header, "Nom complet *", "E-mail", "Rôle *", "Mot de passe temporaire *"};
                    for (int i = 0; i < headers.length; i++) titles.createCell(i).setCellValue(headers[i]);
                    var row = sheet.createRow(1);
                    row.createCell(0).setCellValue(12);
                    row.createCell(1).setCellValue("Test User");
                    row.createCell(3).setCellValue("FORMATRICE");
                    row.createCell(4).setCellValue("Test@2026");
                    var output = new ByteArrayOutputStream();
                    workbook.write(output);
                    var result = service.importFile(actor,
                            new MockMultipartFile("file", "users.xlsx",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    output.toByteArray()));
                    assertThat(result.created()).isEqualTo(1);
                    assertThat(result.errors()).isEmpty();
                }
            }
            verify(users, times(3)).create(eq(actor),
                    eq(new CreateUserRequest("12", "Test User", "", Role.FORMATRICE, "Test@2026")));
        }
    }
}
