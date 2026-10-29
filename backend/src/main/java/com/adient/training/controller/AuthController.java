package com.adient.training.controller;

import com.adient.training.dto.Dtos.*;
import com.adient.training.security.AuthUser;
import com.adient.training.security.AuditFilter;
import com.adient.training.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        request.removeAttribute(AuditFilter.ACTOR);
        request.setAttribute(AuditFilter.USERNAME, req.username().trim());
        request.setAttribute(AuditFilter.DEVICE, req.deviceName());
        LoginResponse response = authService.login(req);
        request.setAttribute(AuditFilter.ACTOR, new AuthUser(response.user().id(), response.user().username(),
                com.adient.training.entity.Role.valueOf(response.user().role())));
        return response;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // JWT remains valid until expiry; the client removes its local token.
    }
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    @PostMapping("/change-password")
    public UserDto changePassword(@AuthenticationPrincipal AuthUser user,
                                  @Valid @RequestBody ChangePasswordRequest req) {
        return authService.changePassword(user.id(), req);
    }
}
