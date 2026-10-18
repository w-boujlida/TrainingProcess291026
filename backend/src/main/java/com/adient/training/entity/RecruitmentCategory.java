package com.adient.training.entity;

public enum RecruitmentCategory {
    CADRE("Cadre"),
    DIRECT("Direct"),
    INDIRECT("Indirect"),
    EXTERNAL("External");

    private final String label;

    RecruitmentCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
