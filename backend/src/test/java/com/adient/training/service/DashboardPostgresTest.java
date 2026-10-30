package com.adient.training.service;

import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "TRAINING_POSTGRES_TEST", matches = "true")
class DashboardPostgresTest {
    @Autowired DashboardService service;
    @Autowired MockMvc mvc;

    @Test void managersSeeHrAndAccountsOthersOnlyFollowup() {
        var admin = service.get(new AuthUser(1L, "superadmin", Role.SUPER_ADMIN));
        assertThat(admin.hr()).isNotNull();
        assertThat(admin.hr().months()).hasSize(12);
        assertThat(admin.accounts().total()).isPositive();
        assertThat(admin.followup().candidates()).isNotNull();
        var trainer = service.get(new AuthUser(2L, "trainer", Role.FORMATRICE));
        assertThat(trainer.hr()).isNull();
        assertThat(trainer.accounts()).isNull();
        assertThat(trainer.followup()).isNotNull();
        assertThatThrownBy(() -> service.get(new AuthUser(3L, "employee", Role.JOB_EMPLOYEE))).isInstanceOf(ApiException.class);
    }

    @Test void endpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }
}
