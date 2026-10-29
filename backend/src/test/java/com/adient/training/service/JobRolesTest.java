package com.adient.training.service;

import com.adient.training.entity.Role;
import com.adient.training.security.AuthUser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JobRolesTest {
    @Test
    void allJobRolesAreUnprivilegedAndFitDatabase() throws Exception {
        var roles = Arrays.stream(Role.values()).filter(r -> r.name().startsWith("JOB_")).toList();
        assertThat(roles).hasSize(52);
        Set<String> labels = new HashSet<>();
        for (var role : roles) {
            assertThat(role.name().length()).isLessThanOrEqualTo(30);
            assertThat(labels.add(role.getLabel().toLowerCase())).isTrue();
            assertThat(role.hasModuleAccess()).isFalse();
            assertThat(role.isUserManager()).isFalse();
            assertThat(Role.SUPER_ADMIN.canManage(role)).isTrue();
            assertThat(Role.ADMIN.canManage(role)).isFalse();
            for (var target : Role.values()) assertThat(role.canManage(target)).isFalse();
        }
        assertThat(Role.FORMATRICE.hasModuleAccess()).isTrue();
        assertThat(Role.TEAM_LEADER.hasModuleAccess()).isTrue();
        var service = new UserService(mock(com.adient.training.repository.UserRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(PasswordPolicy.class));
        var excel = new UserExcelService(service, mock(jakarta.validation.Validator.class), new I18nService());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                excel.template(new AuthUser(1L, "admin", Role.SUPER_ADMIN))))) {
            assertThat(workbook.getSheet("_roles").getLastRowNum()).isEqualTo(Role.values().length - 1);
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_roles"))).isTrue();
            assertThat(workbook.getSheetAt(0).getDataValidations().getFirst()
                    .getValidationConstraint().getFormula1()).isEqualTo("AssignableRoles");
            assertThat(workbook.getSheet("Instructions")).isNotNull();
        }
    }
}
