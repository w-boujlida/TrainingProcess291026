package com.adient.training.controller;

import com.adient.training.dto.Dtos.*;
import com.adient.training.security.AuthUser;
import com.adient.training.service.AuthService;
import jakarta.validation.Valid;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
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
