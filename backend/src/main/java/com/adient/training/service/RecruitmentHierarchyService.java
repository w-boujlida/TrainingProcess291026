package com.adient.training.service;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.entity.WorkShift;
import com.adient.training.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Supervisor → team leader → employees tree built from the HR file (I superviseur, H ligne, J équipe, G poste)
 * and the Cadre / Direct / Indirect dossiers.
 */
@Service
public class RecruitmentHierarchyService {
    private record Row(long userId, String matricule, String fullName, String role, String supervisor, String line, String team,
                       String position, Long candidateId, String category, String shift, Long teamLeaderId,
                       boolean teamLeaderAuto, String teamLeaderName) {}

    private static final String SQL = """
            WITH e AS (
                SELECT u.id, u.username, u.full_name, u.role,
                       max(f->>'value') FILTER (WHERE f->>'column'='I') supervisor,
                       max(f->>'value') FILTER (WHERE f->>'column'='H') line,
                       max(f->>'value') FILTER (WHERE f->>'column'='J') team,
                       max(f->>'value') FILTER (WHERE f->>'column'='G') position
                FROM app_user u
                JOIN user_hr_profile p ON p.user_id = u.id
                CROSS JOIN LATERAL jsonb_array_elements(p.fields) f
                WHERE u.active AND u.work_type IN ('CADRE','DIRECT','INDIRECT')
                GROUP BY u.id
            )
            SELECT e.*, c.id candidate_id, c.category, c.shift, c.team_leader_id, c.team_leader_auto, tl.full_name leader_name
            FROM e
            LEFT JOIN candidate c ON upper(c.matricule) = upper(e.username) AND c.category IN ('CADRE','DIRECT','INDIRECT')
            LEFT JOIN app_user tl ON tl.id = c.team_leader_id
            """;

    private final JdbcTemplate db;
    private final UserRepository users;

    public RecruitmentHierarchyService(JdbcTemplate db, UserRepository users) {
        this.db = db;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Hierarchy hierarchy(RecruitmentCategory category) {
        List<Row> rows = db.query(SQL, (rs, i) -> new Row(rs.getLong("id"), rs.getString("username"),
                rs.getString("full_name"), rs.getString("role"), rs.getString("supervisor"), rs.getString("line"), rs.getString("team"),
                rs.getString("position"), rs.getObject("candidate_id", Long.class), rs.getString("category"),
                rs.getString("shift"), rs.getObject("team_leader_id", Long.class), rs.getBoolean("team_leader_auto"),
                rs.getString("leader_name")));
        List<StaffDto> teamLeaders = users.findAllByRoleInAndActiveTrueOrderByFullNameAsc(CandidateService.TEAM_LEADER_ROLES)
                .stream().map(StaffDto::of).toList();
        return build(rows, category, teamLeaders);
    }

    private static Hierarchy build(List<Row> rows, RecruitmentCategory category, List<StaffDto> teamLeaders) {
        Map<String, List<Long>> leaderGroups = new HashMap<>();
        Map<String, List<Row>> leadersBySupervisor = new HashMap<>();
        for (Row r : rows) {
            if (!RecruitmentHrSync.isTeamLeader(r.position())) continue;
            String group = RecruitmentHrSync.groupKey(r.supervisor(), r.line(), r.team());
            if (group != null && RecruitmentHrSync.canLead(r.role())) leaderGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(r.userId());
            leadersBySupervisor.computeIfAbsent(supervisorKey(r), k -> new ArrayList<>()).add(r);
        }

        Map<String, String> supervisorNames = new HashMap<>();
        Map<String, Map<String, List<HierarchyMember>>> tree = new TreeMap<>(LAST_EMPTY);
        Map<String, String> lineNames = new HashMap<>();
        Map<Long, Integer> membersByLeader = new HashMap<>();
        int members = 0, withoutLeader = 0, withoutShift = 0;
        for (Row r : rows) {
            if (r.candidateId() == null || RecruitmentHrSync.isTeamLeader(r.position())
                    || (category != null && !category.name().equals(r.category()))) continue;
            String supervisor = supervisorKey(r), line = Objects.requireNonNullElse(RecruitmentHrSync.normalize(r.line()), "");
            supervisorNames.putIfAbsent(supervisor, clean(r.supervisor()));
            lineNames.putIfAbsent(line, clean(r.line()));
            List<Long> candidates = leaderGroups.get(RecruitmentHrSync.groupKey(r.supervisor(), r.line(), r.team()));
            Long suggested = candidates != null && candidates.size() == 1 ? candidates.getFirst() : null;
            tree.computeIfAbsent(supervisor, k -> new TreeMap<>(LAST_EMPTY)).computeIfAbsent(line, k -> new ArrayList<>())
                    .add(new HierarchyMember(r.candidateId(), r.matricule(), r.fullName(), r.category(), clean(r.position()),
                            clean(r.team()), r.shift(), label(r.shift()), r.teamLeaderId(), r.teamLeaderName(),
                            r.teamLeaderAuto(), suggested));
            members++;
            if (r.teamLeaderId() == null) withoutLeader++;
            else membersByLeader.merge(r.teamLeaderId(), 1, Integer::sum);
            if (r.shift() == null) withoutShift++;
        }

        List<HierarchySupervisor> supervisors = new ArrayList<>();
        tree.forEach((supervisor, lines) -> {
            List<HierarchyLine> lineList = new ArrayList<>();
            int count = 0, missing = 0;
            for (var entry : lines.entrySet()) {
                List<HierarchyMember> list = entry.getValue();
                list.sort(Comparator.comparing(HierarchyMember::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
                lineList.add(new HierarchyLine(lineNames.get(entry.getKey()), list));
                count += list.size();
                missing += (int) list.stream().filter(m -> m.teamLeaderId() == null).count();
            }
            List<HierarchyLeader> leaders = leadersBySupervisor.getOrDefault(supervisor, List.of()).stream()
                    .sorted(Comparator.comparing((Row r) -> Objects.requireNonNullElse(RecruitmentHrSync.normalize(r.line()), ""))
                            .thenComparing(r -> Objects.requireNonNullElse(r.fullName(), ""), String.CASE_INSENSITIVE_ORDER))
                    .map(r -> new HierarchyLeader(r.userId(), r.fullName(), r.matricule(), clean(r.line()), clean(r.team()),
                            r.candidateId(), r.shift(), label(r.shift()), membersByLeader.getOrDefault(r.userId(), 0),
                            RecruitmentHrSync.canLead(r.role())))
                    .toList();
            supervisors.add(new HierarchySupervisor(supervisorNames.get(supervisor), leaders, lineList, count, missing));
        });
        return new Hierarchy(supervisors, teamLeaders, members, withoutLeader, withoutShift);
    }

    /** Sorts alphabetically with the "unknown" bucket (empty key) last. */
    private static final Comparator<String> LAST_EMPTY = (a, b) ->
            a.isEmpty() == b.isEmpty() ? a.compareTo(b) : a.isEmpty() ? 1 : -1;

    private static String supervisorKey(Row r) {
        return Objects.requireNonNullElse(RecruitmentHrSync.normalize(r.supervisor()), "");
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.strip().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private static String label(String shift) {
        if (shift == null) return null;
        try {
            return WorkShift.valueOf(shift).getLabel();
        } catch (IllegalArgumentException e) {
            return shift;
        }
    }
}
