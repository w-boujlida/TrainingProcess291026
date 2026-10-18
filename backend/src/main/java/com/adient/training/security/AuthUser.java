package com.adient.training.security;

import com.adient.training.entity.Role;

public record AuthUser(Long id, String username, Role role) {}
