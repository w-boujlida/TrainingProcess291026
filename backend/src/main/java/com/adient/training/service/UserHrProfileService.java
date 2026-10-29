package com.adient.training.service;

import com.adient.training.entity.Role;
import com.adient.training.exception.ApiException;
import com.adient.training.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserHrProfileService {
    public record Field(String column, String label, String value) {}
    public record Profile(String sourceFile, String sheetName, LocalDateTime importedAt, List<Field> fields) {}
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public UserHrProfileService(JdbcTemplate db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Profile get(AuthUser actor, long userId) {
        if (actor.role() != Role.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé.");
        var profiles = db.query("SELECT source_file,sheet_name,imported_at,fields::text FROM user_hr_profile WHERE user_id=?",
                (rs, row) -> {
                    try {
                        List<Field> fields = mapper.readValue(rs.getString("fields"), new TypeReference<>() {});
                        return new Profile(rs.getString("source_file"), rs.getString("sheet_name"),
                                rs.getTimestamp("imported_at").toLocalDateTime(), fields);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Fiche RH illisible.", e);
                    }
                }, userId);
        if (profiles.isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "Aucune fiche RH importée pour cet utilisateur.");
        return profiles.getFirst();
    }
}
