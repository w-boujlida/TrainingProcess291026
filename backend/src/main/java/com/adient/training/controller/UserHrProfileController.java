package com.adient.training.controller;

import com.adient.training.security.AuthUser;
import com.adient.training.service.UserHrProfileService;
import com.adient.training.service.UserHrProfileService.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserHrProfileController {
    private final UserHrProfileService service;
    public UserHrProfileController(UserHrProfileService service) { this.service = service; }

    @GetMapping("/{id}/hr-profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Profile> get(@AuthenticationPrincipal AuthUser actor, @PathVariable long id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.get(actor, id));
    }
}
