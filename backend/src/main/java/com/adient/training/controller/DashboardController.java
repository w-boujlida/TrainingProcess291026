package com.adient.training.controller;

import com.adient.training.security.AuthUser;
import com.adient.training.service.DashboardService;
import com.adient.training.service.DashboardService.Dashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Dashboard> get(@AuthenticationPrincipal AuthUser actor) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.get(actor));
    }
}
