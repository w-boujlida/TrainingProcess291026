package com.adient.training.entity;

import java.util.Set;

public enum Role {
    SUPER_ADMIN("Super Admin", 5),
    ADMIN("Admin", 4),
    SUPERVISEUR("Superviseur", 3),
    FORMATRICE("Formatrice", 2),
    TEAM_LEADER("Team Leader", 1);

    private final String label;
    private final int level;

    Role(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String getLabel() { return label; }
    public int getLevel() { return level; }

    /** Super Admin manages every role; Admin manages operational roles only. */
    public boolean canManage(Role target) {
        return switch (this) {
            case SUPER_ADMIN -> true;
            case ADMIN -> Set.of(SUPERVISEUR, FORMATRICE, TEAM_LEADER).contains(target);
            default -> false;
        };
    }

    public boolean isUserManager() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
