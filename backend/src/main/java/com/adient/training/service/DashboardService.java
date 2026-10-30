package com.adient.training.service;

import com.adient.training.exception.ApiException;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;

/**
 * Aggregated dashboards built from HR profiles and follow-up records. Returns counts only, except the list of this
 * month's recruits (name, matricule, position), which every module user can already see in Recrutement.
 */
@Service
public class DashboardService {
    public record Bucket(String key, long count) {}
    public record Month(String month, long hires, long departures) {}
    public record Hr(long employees, long active, long inactive, long hiresThisMonth, long departuresThisMonth,
                     List<Bucket> byType, List<Bucket> byDepartment, List<Bucket> byLine, List<Bucket> gender,
                     List<Bucket> contract, List<Bucket> seniority, List<Bucket> age, List<Month> months) {}
    public record CategoryStatus(String category, String status, long count) {}
    public record Followup(List<CategoryStatus> candidates, long booklets, List<Bucket> bookletOutcome,
                           long bookletCandidateSigned, long bookletTrainerSigned, List<Bucket> objectives,
                           long sheets, long sheetSupervisorSigned, long sheetQualitySigned, long periods,
                           long periodsTrainerSigned, long periodsTeamLeaderSigned, long ratedCompetencies,
                           Double averageRating, List<Bucket> ratings, List<Bucket> workstationSteps) {}
    public record Accounts(long total, long mustChangePassword, long loggedLast7Days, long locked, long neverLogged) {}
    public record Project(String project, long count, long seamstresses, List<Bucket> lines, List<Bucket> positions) {}
    public record Recruit(Long candidateId, String matricule, String fullName, String category, String position,
                          String project, String line, LocalDate hireDate, String shiftLabel, String teamLeader) {}
    /** Direct dossiers by position (as entered in the dossier) and by project (HR line H grouped), plus this month's recruits. */
    public record Workforce(long direct, long seamstresses, List<Bucket> positions, List<Project> projects,
                            String month, List<Bucket> recruitsByCategory, List<Recruit> recruits) {}
    public record Dashboard(Hr hr, Followup followup, Accounts accounts, Workforce workforce) {}

    /** One recruitment dossier with its HR line, reduced to what the workforce dashboard needs. */
    record DossierRow(Long id, String matricule, String fullName, String category, String position, String line,
                      LocalDate hireDate, String shift, String teamLeader) {}

    /** One HR profile, reduced to the columns used by the dashboard. */
    record HrRow(boolean active, String workType, String department, String line, String gender, String contract,
                 String seniority, String age, String hireDate, String departureDate) {}

    static final String NONE = "NON_RENSEIGNE";
    static final String OTHERS = "AUTRES";
    private static final List<String> AGE_ORDER = List.of("< 25", ">25< 35", ">35< 45", ">45< 55", "> 55");
    private static final List<String> SENIORITY_ORDER = List.of("< 1", "< 2", "< 3", "< 4", "> 4");
    private static final int TOP_LINES = 12;

    private final JdbcTemplate db;

    public DashboardService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    public Dashboard get(AuthUser actor) {
        if (!actor.role().hasModuleAccess()) throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé.");
        boolean manager = actor.role().isUserManager();
        return new Dashboard(manager ? hr(hrRows(), YearMonth.now()) : null, followup(), manager ? accounts() : null,
                workforce(dossierRows(), YearMonth.now()));
    }

    private List<DossierRow> dossierRows() {
        return db.query("""
                SELECT c.id, c.matricule, c.full_name, c.category, c.position, c.hire_date, c.shift, tl.full_name leader,
                       (SELECT max(f->>'value') FROM user_hr_profile p CROSS JOIN LATERAL jsonb_array_elements(p.fields) f
                         WHERE p.user_id = u.id AND f->>'column' = 'H') line
                FROM candidate c
                LEFT JOIN app_user u ON lower(u.username) = lower(c.matricule)
                LEFT JOIN app_user tl ON tl.id = c.team_leader_id
                """, (rs, i) -> new DossierRow(rs.getLong("id"), rs.getString("matricule"), rs.getString("full_name"),
                rs.getString("category"), rs.getString("position"), rs.getString("line"),
                rs.getObject("hire_date", LocalDate.class), rs.getString("shift"), rs.getString("leader")));
    }

    static Workforce workforce(List<DossierRow> rows, YearMonth now) {
        List<DossierRow> direct = rows.stream().filter(r -> "DIRECT".equals(r.category())).toList();
        Map<String, String> labels = spellings(direct.stream().map(DossierRow::position).toList());
        Function<DossierRow, String> position = r -> Optional.ofNullable(labels.get(key(r.position()))).orElse(NONE);
        Set<String> bases = new HashSet<>();
        rows.forEach(r -> Optional.ofNullable(lineBase(r.line())).ifPresent(bases::add));
        Function<DossierRow, String> project = r -> project(r.line(), bases);

        Map<String, List<DossierRow>> byProject = new HashMap<>();
        direct.forEach(r -> byProject.computeIfAbsent(project.apply(r), k -> new ArrayList<>()).add(r));
        List<Project> projects = byProject.entrySet().stream()
                .map(e -> new Project(e.getKey(), e.getValue().size(),
                        e.getValue().stream().filter(DashboardService::isSeamstress).count(),
                        countBy(e.getValue(), r -> upper(r.line())), countBy(e.getValue(), position)))
                .sorted(Comparator.comparing((Project p) -> NONE.equals(p.project()))
                        .thenComparing(Comparator.comparingLong(Project::count).reversed()).thenComparing(Project::project))
                .toList();

        List<DossierRow> recruits = rows.stream()
                .filter(r -> r.hireDate() != null && YearMonth.from(r.hireDate()).equals(now))
                .sorted(Comparator.comparing(DossierRow::hireDate).reversed().thenComparing(DossierRow::fullName))
                .toList();
        List<String> categories = List.of("FORMATION", "DIRECT", "INDIRECT", "CADRE");
        Map<String, Long> recruitCounts = recruits.stream().collect(java.util.stream.Collectors.groupingBy(DossierRow::category,
                java.util.stream.Collectors.counting()));
        return new Workforce(direct.size(), direct.stream().filter(DashboardService::isSeamstress).count(),
                countBy(direct, position), projects, now.toString(),
                categories.stream().map(c -> new Bucket(c, recruitCounts.getOrDefault(c, 0L))).toList(),
                recruits.stream().map(r -> new Recruit(r.id(), r.matricule(), r.fullName(), r.category(),
                        r.position() == null ? null : r.position().strip(), project.apply(r), clean(r.line()), r.hireDate(),
                        shiftLabel(r.shift()), r.teamLeader())).toList());
    }

    private static String shiftLabel(String shift) {
        if (shift == null) return null;
        try { return com.adient.training.entity.WorkShift.valueOf(shift).getLabel(); }
        catch (IllegalArgumentException e) { return shift; }
    }

    static boolean isSeamstress(DossierRow r) {
        String k = key(r.position());
        return k != null && k.startsWith("COUTURI");
    }

    /** Case-, accent- and space-insensitive key; null when empty. */
    static String key(String value) {
        String text = clean(value);
        if (text == null) return null;
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    /** key → most frequent spelling, so « prép CAMPO » and « Prép CAMPO » are counted together. */
    static Map<String, String> spellings(List<String> values) {
        Map<String, Map<String, Long>> seen = new HashMap<>();
        values.forEach(v -> { String k = key(v); if (k != null) seen.computeIfAbsent(k, x -> new HashMap<>()).merge(clean(v), 1L, Long::sum); });
        Map<String, String> result = new HashMap<>();
        seen.forEach((k, spellings) -> result.put(k, spellings.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .orElseThrow().getKey()));
        return result;
    }

    /** Line without its side / variant suffix: « P6 AV » → P6, « Skoda 336 » → SKODA. */
    static String lineBase(String line) {
        String text = upper(line);
        if (NONE.equals(text)) return null;
        String base = text.replaceAll("(\\s+(AV|AR|\\d+))+$", "").strip();
        return base.isEmpty() ? text : base;
    }

    /** Project of a production line: its base, or a known project named inside it (« linge renfort Skoda » → SKODA). */
    static String project(String line, Set<String> bases) {
        String base = lineBase(line);
        if (base == null) return NONE;
        if (base.contains(" ")) {
            for (String word : base.split(" ")) if (!word.equals(base) && bases.contains(word)) return word;
        }
        return base;
    }

    private static <T> List<Bucket> countBy(List<T> rows, Function<T, String> key) {
        Map<String, Long> counts = new HashMap<>();
        rows.forEach(r -> counts.merge(key.apply(r), 1L, Long::sum));
        return counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Long> e) -> NONE.equals(e.getKey()))
                        .thenComparing(Map.Entry.<String, Long>comparingByValue().reversed()).thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Bucket(e.getKey(), e.getValue())).toList();
    }

    private List<HrRow> hrRows() {
        return db.query("""
                SELECT u.active, u.work_type,
                       max(f->>'value') FILTER (WHERE f->>'column'='F') department,
                       max(f->>'value') FILTER (WHERE f->>'column'='H') line,
                       max(f->>'value') FILTER (WHERE f->>'column'='AM') gender,
                       max(f->>'value') FILTER (WHERE f->>'column'='T') contract,
                       max(f->>'value') FILTER (WHERE f->>'column'='AE') seniority,
                       max(f->>'value') FILTER (WHERE f->>'column'='AH') age,
                       max(f->>'value') FILTER (WHERE f->>'column'='N') hire,
                       max(f->>'value') FILTER (WHERE f->>'column'='Q') departure
                FROM app_user u
                JOIN user_hr_profile p ON p.user_id = u.id
                CROSS JOIN LATERAL jsonb_array_elements(p.fields) f
                GROUP BY u.id
                """, (rs, i) -> new HrRow(rs.getBoolean("active"), rs.getString("work_type"), rs.getString("department"),
                rs.getString("line"), rs.getString("gender"), rs.getString("contract"), rs.getString("seniority"),
                rs.getString("age"), rs.getString("hire"), rs.getString("departure")));
    }

    static Hr hr(List<HrRow> rows, YearMonth now) {
        List<HrRow> active = rows.stream().filter(HrRow::active).toList();
        Map<String, long[]> months = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) months.put(now.minusMonths(i).toString(), new long[2]);
        for (HrRow row : rows) {
            Optional.ofNullable(months.get(month(row.hireDate()))).ifPresent(m -> m[0]++);
            Optional.ofNullable(months.get(month(row.departureDate()))).ifPresent(m -> m[1]++);
        }
        long[] current = months.get(now.toString());
        return new Hr(rows.size(), active.size(), rows.size() - active.size(), current[0], current[1],
                ordered(active, r -> Optional.ofNullable(r.workType()).orElse(NONE), List.of("CADRE", "DIRECT", "INDIRECT")),
                byCount(active, r -> upper(r.department()), Integer.MAX_VALUE),
                byCount(active, r -> upper(r.line()), TOP_LINES),
                ordered(active, r -> gender(r.gender()), List.of("FEMME", "HOMME")),
                byCount(active, r -> upper(r.contract()), Integer.MAX_VALUE),
                ordered(active, r -> bracket(r.seniority(), SENIORITY_ORDER), SENIORITY_ORDER),
                ordered(active, r -> bracket(r.age(), AGE_ORDER), AGE_ORDER),
                months.entrySet().stream().map(e -> new Month(e.getKey(), e.getValue()[0], e.getValue()[1])).toList());
    }

    private Followup followup() {
        List<CategoryStatus> candidates = db.query(
                "SELECT category, status, count(*) n FROM candidate GROUP BY 1,2 ORDER BY 1,2",
                (rs, i) -> new CategoryStatus(rs.getString(1), rs.getString(2), rs.getLong(3)));
        Map<String, Object> booklet = db.queryForMap("""
                SELECT count(*) total,
                       count(*) FILTER (WHERE candidate_signed_at IS NOT NULL) candidate_signed,
                       count(*) FILTER (WHERE trainer_signed_at IS NOT NULL) trainer_signed
                FROM integration_booklet""");
        Map<String, Object> sheet = db.queryForMap("""
                SELECT (SELECT count(*) FROM training_sheet) total,
                       (SELECT count(*) FROM training_sheet WHERE supervisor_signed_at IS NOT NULL) supervisor_signed,
                       (SELECT count(*) FROM training_sheet WHERE quality_signed_at IS NOT NULL) quality_signed,
                       (SELECT count(*) FROM training_period) periods,
                       (SELECT count(*) FROM training_period WHERE trainer_signed_at IS NOT NULL) periods_trainer,
                       (SELECT count(*) FROM training_period WHERE team_leader_signed_at IS NOT NULL) periods_tl,
                       (SELECT count(rating) FROM training_competency) rated,
                       (SELECT round(avg(rating)::numeric, 2) FROM training_competency) average""");
        Object average = sheet.get("average");
        return new Followup(candidates, num(booklet, "total"),
                buckets("SELECT COALESCE(evaluation_outcome,'PENDING'), count(*) FROM integration_booklet GROUP BY 1 ORDER BY 1"),
                num(booklet, "candidate_signed"), num(booklet, "trainer_signed"),
                buckets("SELECT assessment, count(*) FROM integration_booklet_objective GROUP BY 1 ORDER BY 1"),
                num(sheet, "total"), num(sheet, "supervisor_signed"), num(sheet, "quality_signed"), num(sheet, "periods"),
                num(sheet, "periods_trainer"), num(sheet, "periods_tl"), num(sheet, "rated"),
                average == null ? null : ((Number) average).doubleValue(),
                buckets("SELECT rating::text, count(*) FROM training_competency WHERE rating IS NOT NULL GROUP BY 1 ORDER BY 1"),
                buckets("""
                        SELECT CASE WHEN training_status='NA' THEN 'NA' WHEN evaluation_status IS NOT NULL THEN evaluation_status
                                    ELSE 'PENDING' END, count(*)
                        FROM workstation_step GROUP BY 1 ORDER BY 1"""));
    }

    private Accounts accounts() {
        Map<String, Object> m = db.queryForMap("""
                SELECT count(*) total,
                       count(*) FILTER (WHERE must_change_password) must_change,
                       count(*) FILTER (WHERE last_login_at > now() - interval '7 days') recent,
                       count(*) FILTER (WHERE locked_until > now()) locked,
                       count(*) FILTER (WHERE last_login_at IS NULL) never
                FROM app_user""");
        return new Accounts(num(m, "total"), num(m, "must_change"), num(m, "recent"), num(m, "locked"), num(m, "never"));
    }

    private List<Bucket> buckets(String sql) {
        return db.query(sql, (rs, i) -> new Bucket(rs.getString(1), rs.getLong(2)));
    }

    private static long num(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static List<Bucket> ordered(List<HrRow> rows, Function<HrRow, String> key, List<String> order) {
        Map<String, Long> counts = count(rows, key);
        List<Bucket> result = new ArrayList<>();
        order.forEach(k -> result.add(new Bucket(k, counts.getOrDefault(k, 0L))));
        counts.entrySet().stream().filter(e -> !order.contains(e.getKey()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> result.add(new Bucket(e.getKey(), e.getValue())));
        return result;
    }

    private static List<Bucket> byCount(List<HrRow> rows, Function<HrRow, String> key, int limit) {
        List<Map.Entry<String, Long>> sorted = count(rows, key).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
        List<Bucket> result = new ArrayList<>(sorted.stream().limit(limit).map(e -> new Bucket(e.getKey(), e.getValue())).toList());
        long rest = sorted.stream().skip(limit).mapToLong(Map.Entry::getValue).sum();
        if (rest > 0) result.add(new Bucket(OTHERS, rest));
        return result;
    }

    private static Map<String, Long> count(List<HrRow> rows, Function<HrRow, String> key) {
        Map<String, Long> counts = new HashMap<>();
        rows.forEach(r -> counts.merge(key.apply(r), 1L, Long::sum));
        return counts;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.strip().replaceAll("\\s+", " ");
        return text.isEmpty() || text.startsWith("#") || text.startsWith("Non calculé") ? null : text;
    }

    static String upper(String value) {
        String text = clean(value);
        return text == null ? NONE : text.toUpperCase(Locale.ROOT);
    }

    static String gender(String value) {
        String text = upper(value);
        return text.equals("FEMME") || text.equals("HOMME") ? text : NONE;
    }

    static String bracket(String value, List<String> known) {
        String text = clean(value);
        if (text == null) return NONE;
        return known.contains(text) ? text : OTHERS;
    }

    static String month(String value) {
        String text = clean(value);
        return text != null && text.matches("\\d{4}-\\d{2}.*") ? text.substring(0, 7) : null;
    }
}
