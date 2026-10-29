package com.adient.training.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {
    public record Event(Long actorId, String username, String role, String action, String resource,
                        String method, int status, String ip, String peer, String ipSource,
                        String userAgent, String deviceName, Integer affectedCount, Integer rejectedCount) {}
    public record Entry(long id, Instant occurredAt, Long actorId, String username, String role,
                        String action, String resource, String method, int status, String ip,
                        String peer, String ipSource, String userAgent, String deviceName,
                        Integer affectedCount, Integer rejectedCount) {}
    public record Result(List<Entry> items, long total, int page, int size) {}

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Event event) {
        jdbc.update("""
                INSERT INTO audit_event(actor_id, username, actor_role, action, resource, http_method,
                    http_status, ip_address, peer_address, ip_source, user_agent, device_name,
                    affected_count, rejected_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.actorId(), limit(event.username(), 50), limit(event.role(), 30), event.action(),
                event.resource(), event.method(), event.status(), limit(event.ip(), 64), limit(event.peer(), 64),
                event.ipSource(), limit(event.userAgent(), 512), limit(event.deviceName(), 100),
                event.affectedCount(), event.rejectedCount());
    }

    @Transactional(readOnly = true)
    public Result search(String username, String ip, String action, String outcome,
                         Instant from, Instant to, int page, int size) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE occurred_at >= ?");
        parameters.add(java.sql.Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS)));
        if (username != null && !username.isBlank()) {
            where.append(" AND LOWER(username) = LOWER(?)");
            parameters.add(username.trim());
        }
        if (ip != null && !ip.isBlank()) {
            where.append(" AND ip_address = ?");
            parameters.add(ip.trim());
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?");
            parameters.add(action);
        }
        if ("SUCCESS".equals(outcome)) where.append(" AND http_status < 400");
        if ("FAILURE".equals(outcome)) where.append(" AND http_status >= 400");
        if (from != null) { where.append(" AND occurred_at >= ?"); parameters.add(java.sql.Timestamp.from(from)); }
        if (to != null) { where.append(" AND occurred_at < ?"); parameters.add(java.sql.Timestamp.from(to)); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event" + where, Long.class, parameters.toArray());
        parameters.add(size);
        parameters.add((long) page * size);
        RowMapper<Entry> mapper = (row, index) -> new Entry(row.getLong("id"), row.getTimestamp("occurred_at").toInstant(),
                row.getObject("actor_id", Long.class), row.getString("username"), row.getString("actor_role"),
                row.getString("action"), row.getString("resource"), row.getString("http_method"),
                row.getInt("http_status"), row.getString("ip_address"), row.getString("peer_address"),
                row.getString("ip_source"), row.getString("user_agent"), row.getString("device_name"),
                row.getObject("affected_count", Integer.class), row.getObject("rejected_count", Integer.class));
        return new Result(jdbc.query("SELECT * FROM audit_event" + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", mapper, parameters.toArray()), total, page, size);
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void purgeExpired() {
        jdbc.update("DELETE FROM audit_event WHERE occurred_at < ?",
                java.sql.Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS)));
    }

    private static String limit(String text, int length) {
        if (text == null) return null;
        String clean = text.replaceAll("[\\p{Cntrl}]", " ");
        return clean.substring(0, Math.min(clean.length(), length));
    }
}
