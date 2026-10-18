package com.adient.training.entity;

public enum CandidateStatus {
    CANDIDAT("Candidat"),
    RECRUTE("Recruté"),
    EN_FORMATION("En formation"),
    VALIDE("Validé"),
    REJETE("Rejeté");

    private final String label;

    CandidateStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
