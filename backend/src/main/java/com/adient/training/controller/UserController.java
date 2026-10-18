package com.adient.training.controller;

import com.adient.training.dto.Dtos.*;
import com.adient.training.security.AuthUser;
import com.adient.training.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    @GetMapping("/roles")
    public List<RoleDto> assignableRoles(@AuthenticationPrincipal AuthUser actor) {
        return userService.assignableRoles(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody CreateUserRequest req) {
        return userService.create(actor, req);
    }

    @PutMapping("/{id}")
    public UserDto update(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(actor, id, req);
    }

    @PatchMapping("/{id}/status")
    public UserDto setStatus(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                             @Valid @RequestBody StatusRequest req) {
        return userService.setStatus(actor, id, req.active());
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id) {
        return userService.resetPassword(actor, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id) {
        userService.delete(actor, id);
    }
}
