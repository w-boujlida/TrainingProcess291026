package com.adient.training.service;

import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.security.AuthUser;
import com.adient.training.service.HrWorkbookReader.*;
import com.adient.training.service.UserHrProfileService.Field;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class UserHrImportService {
    public record Recruit(long id, String username, String fullName, boolean active, String temporaryPassword) {}
    public record Preview(String confirmation, int rows, int updates, int creates, int protectedAccounts,
                          int excelErrors, int uncalculated, List<Issue> errors, List<Recruit> newRecruits) {}
    public record Result(int updated, int created, int protectedAccounts, List<Recruit> newRecruits,
                         RecruitmentHrSync.Summary recruitment) {}
    private record Account(long id, String username, Role role) {}
    private final JdbcTemplate db;
    private final HrWorkbookReader reader;
    private final ObjectMapper mapper;
    private final PasswordPolicy passwords;
    private final PasswordEncoder encoder;

    public UserHrImportService(JdbcTemplate db, HrWorkbookReader reader, ObjectMapper mapper,
                               PasswordPolicy passwords, PasswordEncoder encoder) {
        this.db = db;
        this.reader = reader;
        this.mapper = mapper;
        this.passwords = passwords;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Preview preview(AuthUser actor, MultipartFile file) {
        requireSuperAdmin(actor);
        WorkbookData data = reader.read(file);
        return preview(data, accounts());
    }

    @Transactional
    public Result apply(AuthUser actor, MultipartFile file, String confirmation) {
        requireSuperAdmin(actor);
        WorkbookData data = reader.read(file);
        // Serialize imports and ordinary account/profile writes between preview validation and commit.
        db.execute("SET LOCAL lock_timeout = '10s'");
        try {
            db.execute("LOCK TABLE app_user,user_hr_profile,candidate IN SHARE ROW EXCLUSIVE MODE");
        } catch (CannotAcquireLockException e) {
            throw new ApiException(HttpStatus.CONFLICT, "Une mise à jour est en cours. Recommencez l’aperçu dans quelques instants.");
        }
        Map<String, Account> accounts = accounts();
        Preview preview = preview(data, accounts);
        if (confirmation == null || !MessageDigest.isEqual(preview.confirmation().getBytes(StandardCharsets.UTF_8),
                confirmation.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.CONFLICT, "Les données ont changé. Recommencez l’aperçu avant de confirmer.");
        }
        if (!preview.errors().isEmpty()) throw bad("Corrigez les lignes en erreur avant de confirmer l’import.");
        List<Recruit> recruits = new ArrayList<>();
        int updated = 0;
        for (Employee employee : data.employees()) {
            Account account = accounts.get(employee.username().toLowerCase(Locale.ROOT));
            if (account != null && account.role().hasModuleAccess()) continue;
            long id;
            if (account == null) {
                String temporary = passwords.generateTemporary();
                id = Objects.requireNonNull(db.queryForObject(
                        "INSERT INTO app_user(username,full_name,active,work_type,role,password_hash,must_change_password) " +
                                "VALUES (?,?,?,?,'JOB_EMPLOYEE',?,true) RETURNING id", Long.class,
                        employee.username(), employee.fullName(), employee.active(), employee.workType(), encoder.encode(temporary)));
                recruits.add(new Recruit(id, employee.username(), employee.fullName(), employee.active(), temporary));
            } else {
                id = account.id();
                db.update("UPDATE app_user SET full_name=COALESCE(NULLIF(?,''),full_name),active=COALESCE(?,active)," +
                                "work_type=COALESCE(?,work_type),updated_at=NOW() WHERE id=?",
                        employee.fullName(), employee.active(), employee.workType(), id);
                updated++;
            }
            List<Field> fields = merge(existingFields(id), employee.fields());
            try {
                db.update("INSERT INTO user_hr_profile(user_id,source_file,sheet_name,fields) VALUES (?,?,'RH',?::jsonb) " +
                                "ON CONFLICT(user_id) DO UPDATE SET source_file=EXCLUDED.source_file,sheet_name='RH'," +
                                "fields=EXCLUDED.fields,imported_at=NOW()",
                        id, data.filename(), mapper.writeValueAsString(fields));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Impossible d’enregistrer la fiche RH.", e);
            }
        }
        return new Result(updated, recruits.size(), preview.protectedAccounts(), recruits, new RecruitmentHrSync(db).sync());
    }

    private Preview preview(WorkbookData data, Map<String, Account> accounts) {
        int updates = 0, protectedAccounts = 0;
        List<Recruit> recruits = new ArrayList<>();
        List<Issue> errors = new ArrayList<>(data.errors());
        for (Employee employee : data.employees()) {
            Account account = accounts.get(employee.username().toLowerCase(Locale.ROOT));
            if (account == null) {
                if (employee.fullName().isBlank() || employee.active() == null || employee.workType() == null) {
                    errors.add(new Issue(employee.row(), "Un nouveau compte exige nom, statut actif/inactif et type de travail."));
                } else {
                    recruits.add(new Recruit(0, employee.username(), employee.fullName(), employee.active(), null));
                }
            } else if (account.role().hasModuleAccess()) {
                protectedAccounts++;
            } else {
                updates++;
            }
        }
        return new Preview(confirmation(data.bytes()), data.employees().size() + data.errors().size(), updates,
                recruits.size(), protectedAccounts, data.excelErrors(), data.uncalculated(), errors, recruits);
    }

    private Map<String, Account> accounts() {
        Map<String, Account> result = new HashMap<>();
        db.query("SELECT id,username,role FROM app_user", rs -> {
            result.put(rs.getString("username").toLowerCase(Locale.ROOT),
                    new Account(rs.getLong("id"), rs.getString("username"), Role.valueOf(rs.getString("role"))));
        });
        return result;
    }

    private List<Field> existingFields(long id) {
        List<String> rows = db.queryForList("SELECT fields::text FROM user_hr_profile WHERE user_id=?", String.class, id);
        if (rows.isEmpty()) return List.of();
        try {
            return mapper.readValue(rows.getFirst(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Fiche RH illisible.", e);
        }
    }

    static List<Field> merge(List<Field> existing, List<Field> incoming) {
        Map<String, Field> old = new HashMap<>();
        existing.forEach(field -> old.put(field.column(), field));
        return incoming.stream().map(field -> field.value().isBlank() && old.containsKey(field.column())
                ? new Field(field.column(), field.label(), old.get(field.column()).value()) : field).toList();
    }

    private String confirmation(byte[] file) {
        String state = db.queryForObject("""
                SELECT
                  (SELECT md5(COALESCE(string_agg(md5(row_to_json(u)::text),'' ORDER BY id),'')) FROM app_user u)
                  || (SELECT md5(COALESCE(string_agg(md5(row_to_json(p)::text),'' ORDER BY user_id),'')) FROM user_hr_profile p)
                """, String.class);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file);
            digest.update(state.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible.", e);
        }
    }
    private static void requireSuperAdmin(AuthUser actor) {
        if (actor.role() != Role.SUPER_ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé.");
    }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
