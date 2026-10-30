package com.adient.training.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Places HR employees in the Recruitment tab matching their TYPE DE TRAVAIL (Cadre / Direct / Indirect).
 * Existing dossiers linked by matricule are moved; active employees without a dossier get one "En formation".
 * Formation dossiers are left in their onboarding workflow.
 */
public class RecruitmentHrSync {
    public record Summary(int created, int moved, int withoutDossier, int shifts, int teamLeaders) {}

    private record Employee(long id, String username, String fullName, String workType, boolean active, String cin,
                            LocalDate birthDate, LocalDate hireDate, String phone, String address, String position,
                            String department, String educationLevel, String team, String supervisor, String line,
                            String role) {}

    private static final Set<String> TYPES = Set.of("CADRE", "DIRECT", "INDIRECT");
    private static final String HR_SQL = """
            SELECT u.id, u.username, u.full_name, u.work_type, u.active, u.role,
                   max(f->>'value') FILTER (WHERE f->>'column'='AK') cin,
                   max(f->>'value') FILTER (WHERE f->>'column'='AF') birth,
                   max(f->>'value') FILTER (WHERE f->>'column'='N') hire,
                   max(f->>'value') FILTER (WHERE f->>'column'='BG') phone,
                   max(f->>'value') FILTER (WHERE f->>'column'='BC') address,
                   max(f->>'value') FILTER (WHERE f->>'column'='BD') commune,
                   max(f->>'value') FILTER (WHERE f->>'column'='G') position,
                   max(f->>'value') FILTER (WHERE f->>'column'='F') department,
                   max(f->>'value') FILTER (WHERE f->>'column'='AP') education,
                   max(f->>'value') FILTER (WHERE f->>'column'='J') team,
                   max(f->>'value') FILTER (WHERE f->>'column'='I') supervisor,
                   max(f->>'value') FILTER (WHERE f->>'column'='H') line
            FROM app_user u
            JOIN user_hr_profile p ON p.user_id = u.id
            CROSS JOIN LATERAL jsonb_array_elements(p.fields) f
            WHERE u.work_type IN ('CADRE','DIRECT','INDIRECT')
            GROUP BY u.id
            ORDER BY u.id
            """;

    private final JdbcTemplate db;

    public RecruitmentHrSync(JdbcTemplate db) {
        this.db = db;
    }

    /** Schema needed by the sync (idempotent, used by the migrations): HR teams A / B as shifts, auto-linked team leaders. */
    public static void prepareSchema(JdbcTemplate db) {
        db.execute("ALTER TABLE candidate DROP CONSTRAINT IF EXISTS candidate_shift_check");
        db.execute("ALTER TABLE candidate ADD CONSTRAINT candidate_shift_check CHECK (shift IN " +
                "('MATIN','APRES_MIDI','NUIT','NORMAL','EQUIPE_A','EQUIPE_B'))");
        db.execute("ALTER TABLE candidate ADD COLUMN IF NOT EXISTS team_leader_auto BOOLEAN NOT NULL DEFAULT FALSE");
    }

    public Summary sync() {
        List<Employee> employees = db.query(HR_SQL, (rs, i) -> new Employee(
                rs.getLong("id"), rs.getString("username"), rs.getString("full_name"), rs.getString("work_type"),
                rs.getBoolean("active"), cin(rs.getString("cin")), date(rs.getString("birth")), date(rs.getString("hire")),
                cut(rs.getString("phone"), 30), cut(join(rs.getString("address"), rs.getString("commune")), 255),
                cut(rs.getString("position"), 120), cut(rs.getString("department"), 120),
                cut(rs.getString("education"), 120), rs.getString("team"), rs.getString("supervisor"), rs.getString("line"),
                rs.getString("role")));

        Map<String, String> categoryByMatricule = new HashMap<>();
        Set<String> usedCins = new HashSet<>();
        db.query("SELECT matricule, cin, category FROM candidate", rs -> {
            if (rs.getString("matricule") != null)
                categoryByMatricule.put(key(rs.getString("matricule")), rs.getString("category"));
            usedCins.add(key(rs.getString("cin")));
        });

        List<Object[]> moves = new ArrayList<>();
        Map<String, Employee> toCreate = new LinkedHashMap<>();
        for (Employee employee : employees) {
            String current = categoryByMatricule.get(key(employee.username()));
            if (current != null) {
                if (TYPES.contains(current) && !current.equals(employee.workType()))
                    moves.add(new Object[]{employee.workType(), key(employee.username())});
                continue;
            }
            if (!employee.active() || employee.cin() == null || employee.username().length() > 30
                    || usedCins.contains(key(employee.cin()))) continue;
            // Duplicate CIN in the HR file: keep the most recent hire (then the most recent account).
            toCreate.merge(key(employee.cin()), employee, (a, b) -> newer(b, a) ? b : a);
        }

        int moved = Arrays.stream(db.batchUpdate(
                "UPDATE candidate SET category=?, updated_at=NOW() WHERE upper(matricule)=?", moves)).sum();
        List<Object[]> inserts = toCreate.values().stream().map(e -> new Object[]{
                e.workType(), e.username(), cut(blankToNull(e.fullName()) == null ? e.username() : e.fullName(), 120),
                e.cin(), sqlDate(e.birthDate()), e.phone(), e.address(), e.position(), e.department(),
                sqlDate(e.hireDate()), e.educationLevel()}).toList();
        db.batchUpdate("INSERT INTO candidate(category,status,matricule,full_name,cin,birth_date,phone,address,position," +
                "department,hire_date,education_level,created_by,created_at,updated_at) " +
                "VALUES (?,'EN_FORMATION',?,?,?,?,?,?,?,?,?,?,'IMPORT_RH',NOW(),NOW())", inserts);

        Map<String, String> currentShift = new HashMap<>();
        db.query("SELECT matricule, shift FROM candidate WHERE matricule IS NOT NULL AND category IN ('CADRE','DIRECT','INDIRECT')",
                rs -> { currentShift.put(key(rs.getString("matricule")), rs.getString("shift")); });
        List<Object[]> shiftUpdates = new ArrayList<>();
        for (Employee employee : employees) {
            String matricule = key(employee.username());
            if (!currentShift.containsKey(matricule)) continue;
            String current = currentShift.get(matricule);
            String target = shift(employee.team(), employee.workType(), current);
            if (target != null && !target.equals(current)) shiftUpdates.add(new Object[]{target, matricule});
        }
        int shifts = Arrays.stream(db.batchUpdate(
                "UPDATE candidate SET shift=?, updated_at=NOW() WHERE upper(matricule)=?", shiftUpdates)).sum();
        int teamLeaders = syncTeamLeaders(employees);

        Integer missing = db.queryForObject("""
                SELECT count(*) FROM app_user u
                WHERE u.active AND u.work_type IN ('CADRE','DIRECT','INDIRECT')
                  AND EXISTS (SELECT 1 FROM user_hr_profile p WHERE p.user_id = u.id)
                  AND NOT EXISTS (SELECT 1 FROM candidate c WHERE upper(c.matricule) = upper(u.username))
                """, Integer.class);
        return new Summary(inserts.size(), moved, missing == null ? 0 : missing, shifts, teamLeaders);
    }

    private record Dossier(long id, Long teamLeaderId, boolean auto) {}

    /**
     * The HR file only names the supervisor (column I). An employee is linked to the team leader sharing the same
     * supervisor, line (H) and team (J) when exactly one exists. Manual choices are never overwritten; automatic
     * links are refreshed or cleared when the HR data changes.
     */
    private int syncTeamLeaders(List<Employee> employees) {
        Map<String, List<Long>> leaders = new HashMap<>();
        for (Employee e : employees) {
            String group = groupKey(e.supervisor(), e.line(), e.team());
            if (e.active() && isTeamLeader(e.position()) && canLead(e.role()) && group != null)
                leaders.computeIfAbsent(group, k -> new ArrayList<>()).add(e.id());
        }
        Map<String, Dossier> dossiers = new HashMap<>();
        db.query("SELECT id, matricule, team_leader_id, team_leader_auto FROM candidate " +
                        "WHERE matricule IS NOT NULL AND category IN ('CADRE','DIRECT','INDIRECT')",
                rs -> { dossiers.put(key(rs.getString("matricule")), new Dossier(rs.getLong("id"),
                        rs.getObject("team_leader_id", Long.class), rs.getBoolean("team_leader_auto"))); });
        List<Object[]> updates = new ArrayList<>();
        for (Employee e : employees) {
            Dossier d = dossiers.get(key(e.username()));
            if (d == null || isTeamLeader(e.position()) || (d.teamLeaderId() != null && !d.auto())) continue;
            Long match = e.active() ? uniqueLeader(leaders, groupKey(e.supervisor(), e.line(), e.team())) : null;
            if (Objects.equals(match, d.teamLeaderId())) continue;
            updates.add(new Object[]{match, match != null, d.id()});
        }
        return Arrays.stream(db.batchUpdate(
                "UPDATE candidate SET team_leader_id=?, team_leader_auto=?, updated_at=NOW() WHERE id=?", updates)).sum();
    }

    private static Long uniqueLeader(Map<String, List<Long>> leaders, String group) {
        List<Long> found = group == null ? null : leaders.get(group);
        return found != null && found.size() == 1 ? found.getFirst() : null;
    }

    static boolean isTeamLeader(String position) {
        return position != null && position.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").startsWith("TEAM LEADER");
    }

    /** Only accounts with a team-leader role can be selected as a dossier's team leader. */
    static boolean canLead(String role) {
        return CandidateService.TEAM_LEADER_ROLES.stream().anyMatch(r -> r.name().equals(role));
    }

    /** Supervisor + line + team, normalised; null when the supervisor or the line is unknown. */
    static String groupKey(String supervisor, String line, String team) {
        String s = normalize(supervisor), l = normalize(line);
        if (s == null || l == null) return null;
        String t = normalize(team);
        return s + "|" + l + "|" + (t == null ? "" : t);
    }

    static String normalize(String value) {
        String text = blankToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * HR column J "Poste" holds the rotating team (A / B). Cadres without a team work normal hours;
     * otherwise a manually assigned shift is kept.
     */
    static String shift(String team, String workType, String current) {
        String value = blankToNull(team);
        if (value != null) {
            String normalized = value.toUpperCase(Locale.ROOT).replaceAll("^(POSTE|EQUIPE|ÉQUIPE)\\s*", "");
            if (normalized.equals("A")) return "EQUIPE_A";
            if (normalized.equals("B")) return "EQUIPE_B";
        }
        return "CADRE".equals(workType) && current == null ? "NORMAL" : null;
    }

    private static boolean newer(Employee candidate, Employee current) {
        Comparator<Employee> order = Comparator.comparing(Employee::hireDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingLong(Employee::id);
        return order.compare(candidate, current) > 0;
    }

    static String cin(String value) {
        String cin = blankToNull(value);
        if (cin == null || cin.length() > 20) return null;
        // Numeric CIN cells lose their leading zero in Excel.
        return cin.matches("\\d{1,7}") ? "0".repeat(8 - cin.length()) + cin : cin;
    }

    static LocalDate date(String value) {
        String text = blankToNull(value);
        if (text == null || text.length() < 10) return null;
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String text = value.strip();
        return text.isEmpty() || text.startsWith("#") || text.startsWith("Non calculé") ? null : text;
    }

    private static String join(String address, String commune) {
        String a = blankToNull(address), c = blankToNull(commune);
        return a == null ? c : c == null ? a : a + ", " + c;
    }

    private static String cut(String value, int max) {
        String text = blankToNull(value);
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private static String key(String value) { return value == null ? null : value.strip().toUpperCase(Locale.ROOT); }
    private static Date sqlDate(LocalDate date) { return date == null ? null : Date.valueOf(date); }
}
