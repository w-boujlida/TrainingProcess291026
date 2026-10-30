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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired RecruitmentHierarchyService hierarchy;
    @Autowired CandidateService candidates;
    @Autowired jakarta.persistence.EntityManager em;
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
    private String id(String prefix) { return prefix + UUID.randomUUID().toString().substring(0, 20); }
    private String category(String matricule) {
        return db.queryForObject("SELECT category||':'||status FROM candidate WHERE matricule=?", String.class, matricule);
    }
    private int dossiers(String matricule) {
        return db.queryForObject("SELECT COUNT(*) FROM candidate WHERE matricule=?", Integer.class, matricule);
    }
    @Test void applyPlacesEmployeesInRecruitmentCategoryByWorkType() throws Exception {
        String movedUser = id("mv-"), formationUser = id("fo-"), fresh = id("nw-"), older = id("od-"), newer = id("nr-"),
                inactive = id("in-"), takenCin = id("tk-"), noCin = id("nc-");
        String cin = "T" + UUID.randomUUID().toString().substring(0, 10), shared = "S" + UUID.randomUUID().toString().substring(0, 10),
                used = "U" + UUID.randomUUID().toString().substring(0, 10);
        db.update("INSERT INTO candidate(category,status,matricule,full_name,cin,created_at,updated_at) VALUES " +
                        "('DIRECT','VALIDE',?,'Moved',?,NOW(),NOW()),('FORMATION','CANDIDAT',?,'Formation',?,NOW(),NOW())," +
                        "('INDIRECT','CANDIDAT',NULL,'Existing',?,NOW(),NOW())",
                movedUser, "M" + UUID.randomUUID().toString().substring(0, 10), formationUser,
                "F" + UUID.randomUUID().toString().substring(0, 10), used);
        var file = workbook(sheet -> {
            employee(sheet, 1, movedUser, "Moved").getCell(10).setCellValue("CADRE");
            employee(sheet, 2, formationUser, "Formation").getCell(10).setCellValue("INDIRECTS");
            var row = employee(sheet, 3, fresh, "Fresh");
            row.getCell(10).setCellValue("INDIRECTS");
            row.createCell(36).setCellValue(cin);
            row.createCell(13).setCellValue("2025-03-04");
            row.createCell(31).setCellValue("1990-05-06");
            row.createCell(6).setCellValue("Couturière");
            row.createCell(9).setCellValue("B");
            var a = employee(sheet, 4, older, "Older"); a.createCell(36).setCellValue(shared); a.createCell(13).setCellValue("2020-01-01");
            var b = employee(sheet, 5, newer, "Newer"); b.createCell(36).setCellValue(shared); b.createCell(13).setCellValue("2024-01-01");
            b.createCell(9).setCellValue("A");
            var c = employee(sheet, 6, inactive, "Inactive"); c.getCell(0).setCellValue("Non-Actif"); c.createCell(36).setCellValue("I" + UUID.randomUUID().toString().substring(0, 10));
            employee(sheet, 7, takenCin, "Taken").createCell(36).setCellValue(used);
            employee(sheet, 8, noCin, "No CIN");
        });
        var result = service.apply(actor, file, service.preview(actor, file).confirmation());
        assertThat(category(movedUser)).isEqualTo("CADRE:VALIDE");
        assertThat(category(formationUser)).isEqualTo("FORMATION:CANDIDAT");
        assertThat(category(fresh)).isEqualTo("INDIRECT:EN_FORMATION");
        assertThat(db.queryForObject("SELECT cin||'|'||hire_date||'|'||birth_date||'|'||position||'|'||created_by FROM candidate WHERE matricule=?",
                String.class, fresh)).isEqualTo(cin + "|2025-03-04|1990-05-06|Couturière|IMPORT_RH");
        assertThat(category(newer)).isEqualTo("DIRECT:EN_FORMATION");
        assertThat(dossiers(older) + dossiers(inactive) + dossiers(takenCin) + dossiers(noCin)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM training_sheet t JOIN candidate c ON c.id=t.candidate_id WHERE c.matricule=?",
                Integer.class, fresh)).isEqualTo(1);
        assertThat(result.recruitment().created()).isGreaterThanOrEqualTo(2);
        assertThat(result.recruitment().moved()).isGreaterThanOrEqualTo(1);
        assertThat(result.recruitment().withoutDossier()).isGreaterThanOrEqualTo(3);
        assertThat(shift(movedUser) + "|" + shift(fresh) + "|" + shift(newer) + "|" + shift(formationUser))
                .isEqualTo("NORMAL|EQUIPE_B|EQUIPE_A|null");
        assertThat(result.recruitment().shifts()).isGreaterThanOrEqualTo(3);
        db.update("UPDATE candidate SET shift='MATIN' WHERE matricule=?", movedUser);
        var again = service.apply(actor, file, service.preview(actor, file).confirmation());
        assertThat(again.recruitment().created()).isZero();
        assertThat(again.recruitment().moved()).isZero();
        assertThat(shift(movedUser)).isEqualTo("MATIN");
    }
    private String shift(String matricule) {
        em.flush();
        return db.queryForObject("SELECT COALESCE(shift,'null') FROM candidate WHERE matricule=?", String.class, matricule);
    }
    private Long candidateId(String matricule) {
        return db.queryForObject("SELECT id FROM candidate WHERE matricule=?", Long.class, matricule);
    }
    private Long userId(String username) {
        return db.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
    private String leader(String matricule) {
        em.flush();
        return db.queryForObject("SELECT COALESCE(team_leader_id::text,'null')||':'||team_leader_auto FROM candidate WHERE matricule=?",
                String.class, matricule);
    }
    private static void hier(org.apache.poi.ss.usermodel.Row row, String position, String supervisor, String line, String team, String type) {
        row.createCell(6).setCellValue(position);
        row.createCell(7).setCellValue(line);
        row.createCell(8).setCellValue(supervisor);
        row.createCell(9).setCellValue(team);
        row.getCell(10).setCellValue(type);
        row.createCell(36).setCellValue("H" + UUID.randomUUID().toString().substring(0, 10));
    }
    @Test void bulkAssignmentIsRestrictedToRecruitmentEditors() throws Exception {
        String m1 = id("s1-"), m2 = id("s2-");
        db.update("INSERT INTO candidate(category,status,matricule,full_name,cin,created_at,updated_at) VALUES " +
                "('DIRECT','EN_FORMATION',?,'One',?,NOW(),NOW()),('DIRECT','EN_FORMATION',?,'Two',?,NOW(),NOW())",
                m1, "C" + UUID.randomUUID().toString().substring(0, 10), m2, "D" + UUID.randomUUID().toString().substring(0, 10));
        Long id1 = candidateId(m1), id2 = candidateId(m2);
        var teamLeader = account(Role.JOB_TEAM_LEADER);
        String body = "{\"ids\":[" + id1 + "," + id2 + "],\"changeShift\":true,\"shift\":\"EQUIPE_B\",\"changeTeamLeader\":true,\"teamLeaderId\":"
                + teamLeader.getId() + "}";
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json").content(body)
                .header("Authorization", "Bearer " + jwt.generate(account(Role.TEAM_LEADER), null))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json").content(body)
                .header("Authorization", "Bearer " + jwt.generate(account(Role.FORMATRICE), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].shiftLabel").value("\u00c9quipe B"))
                .andExpect(jsonPath("$[0].teamLeader.id").value(teamLeader.getId()))
                .andExpect(jsonPath("$[0].teamLeaderAuto").value(false));
        assertThat(shift(m1) + "|" + shift(m2)).isEqualTo("EQUIPE_B|EQUIPE_B");
        String admin = "Bearer " + jwt.generate(account(Role.ADMIN), null);
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json")
                .content("{\"ids\":[" + id1 + "],\"changeShift\":true,\"shift\":null}")
                .header("Authorization", admin)).andExpect(status().isOk());
        assertThat(shift(m1) + "|" + leader(m1)).isEqualTo("null|" + teamLeader.getId() + ":false");
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json")
                .content("{\"ids\":[" + id1 + "],\"changeTeamLeader\":true,\"teamLeaderId\":" + account(Role.FORMATRICE).getId() + "}")
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json")
                .content("{\"ids\":[" + id1 + "]}").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json")
                .content("{\"ids\":[],\"changeShift\":true,\"shift\":\"MATIN\"}").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/recruitment/candidates/assignment").contentType("application/json")
                .content("{\"ids\":[-1],\"changeShift\":true,\"shift\":\"MATIN\"}").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/recruitment/hierarchy").param("category", "DIRECT").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supervisors").isArray());
    }
    @Test void hrImportLinksEmployeesToTheirUniqueTeamLeaderAndBuildsTheHierarchy() throws Exception {
        String supervisor = "Sup " + UUID.randomUUID().toString().substring(0, 8);
        String tl = id("tl-"), tlB1 = id("tb-"), tlB2 = id("tc-"), sewA = id("ca-"), sewB = id("cb-"), sewAudi = id("cc-");
        var file = workbook(sheet -> {
            hier(employee(sheet, 1, tl, "Leader P6"), "Team leader", supervisor, "P6 AV", "A", "INDIRECTS");
            hier(employee(sheet, 2, sewA, "Sew A"), "Couturi\u00e8re", supervisor.toUpperCase() + " ", "P6  AV", "a", "DIRECTS");
            hier(employee(sheet, 3, sewB, "Sew B"), "Couturi\u00e8re", supervisor, "P6 AV", "B", "DIRECTS");
            hier(employee(sheet, 4, tlB1, "Leader Audi 1"), "Team Leader", supervisor, "Audi", "A", "INDIRECTS");
            hier(employee(sheet, 5, tlB2, "Leader Audi 2"), "Team leader", supervisor, "Audi", "A", "INDIRECTS");
            hier(employee(sheet, 6, sewAudi, "Sew Audi"), "Couturi\u00e8re", supervisor, "Audi", "A", "DIRECTS");
        });
        service.apply(actor, file, service.preview(actor, file).confirmation());
        assertThat(leader(sewA)).isEqualTo("null:false");
        db.update("UPDATE app_user SET role='JOB_TEAM_LEADER' WHERE username IN (?,?,?)", tl, tlB1, tlB2);
        var result = service.apply(actor, file, service.preview(actor, file).confirmation());
        Long tlId = userId(tl);
        assertThat(leader(sewA)).isEqualTo(tlId + ":true");
        assertThat(leader(sewB) + "|" + leader(sewAudi)).isEqualTo("null:false|null:false");
        assertThat(result.recruitment().teamLeaders()).isGreaterThanOrEqualTo(1);
        assertThat(shift(sewA) + "|" + shift(tl)).isEqualTo("EQUIPE_A|EQUIPE_A");

        var tree = hierarchy.hierarchy(RecruitmentCategory.DIRECT).supervisors().stream()
                .filter(s -> supervisor.equalsIgnoreCase(s.name())).findFirst().orElseThrow();
        assertThat(tree.members()).isEqualTo(3);
        assertThat(tree.withoutTeamLeader()).isEqualTo(2);
        assertThat(tree.lines()).extracting(l -> l.line()).containsExactly("Audi", "P6 AV");
        assertThat(tree.teamLeaders()).hasSize(3).allMatch(l -> l.assignable());
        assertThat(tree.teamLeaders().stream().filter(l -> l.userId().equals(tlId)).findFirst().orElseThrow().members()).isEqualTo(1);
        var p6 = tree.lines().get(1).members();
        assertThat(p6).extracting(m -> m.fullName()).containsExactly("Sew A", "Sew B");
        assertThat(p6.get(0).suggestedTeamLeaderId()).isEqualTo(tlId);
        assertThat(p6.get(0).teamLeaderAuto()).isTrue();
        assertThat(tree.lines().get(0).members().get(0).suggestedTeamLeaderId()).isNull();

        candidates.assign(new com.adient.training.dto.RecruitmentDtos.AssignmentRequest(
                java.util.List.of(candidateId(sewB)), false, null, true, userId(tlB1)));
        em.flush();
        service.apply(actor, file, service.preview(actor, file).confirmation());
        assertThat(leader(sewB)).isEqualTo(userId(tlB1) + ":false");
        assertThatThrownBy(() -> candidates.assign(new com.adient.training.dto.RecruitmentDtos.AssignmentRequest(
                java.util.List.of(candidateId(tl)), false, null, true, tlId))).isInstanceOf(ApiException.class);
    }
}
