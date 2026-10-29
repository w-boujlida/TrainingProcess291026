package com.adient.training.controller;

import com.adient.training.exception.ApiException;
import com.adient.training.service.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit-events")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Validated
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) { this.service = service; }

    @GetMapping
    public AuditService.Result search(@RequestParam(required = false) @Size(max = 50) String username,
                                      @RequestParam(required = false) @Size(max = 64) String ip,
                                      @RequestParam(required = false) @Size(max = 80) String action,
                                      @RequestParam(required = false) String outcome,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de fin doit être après la date de début.");
        }
        if (outcome != null && !outcome.isBlank() && !outcome.equals("SUCCESS") && !outcome.equals("FAILURE")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Résultat invalide.");
        }
        return service.search(username, ip, action, outcome, from, to, page, size);
    }
}
