package com.adient.training.entity;

public enum WorkShift {
    MATIN("Matin"),
    APRES_MIDI("Après-midi"),
    NUIT("Nuit"),
    NORMAL("Normal"),
    EQUIPE_A("Équipe A"),
    EQUIPE_B("Équipe B");

    private final String label;

    WorkShift(String label) { this.label = label; }

    public String getLabel() { return label; }
}
