package com.adient.training.entity;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum RecruitmentCategory {
    CADRE("Cadre", List.of()),
    DIRECT("Direct", List.of(
            "Préparation", "Assemblage", "Retouche / Reprise", "Campo", "Préparation Campo",
            "Fermeture Invisible", "Tresse / Ba2", "Profil", "Airbag",
            "Surpiqure simple joue", "Surpiqure double joue", "Surpiqure simple coiffe", "Surpiqure double coiffe",
            "Finition Poche / Joue", "Finition coiffe", "TIEDAWN", "GLASSAGE TOILLE AIRBAG",
            "ASSEMBLAGE NON AIRBAG + RENFORT AIRBAG", "FERMETURE SAC AIRBAG", "Wire", "Alimentatrice",
            "Contrôle Qualité")),
    INDIRECT("Indirect", List.of()),
    EXTERNAL("External", List.of());

    private final String label;
    /** Allowed positions; empty means free text. */
    private final List<String> positions;

    RecruitmentCategory(String label, List<String> positions) {
        this.label = label;
        this.positions = positions;
    }

    public String getLabel() { return label; }

    public List<String> getPositions() { return positions; }

    public boolean hasFixedPositions() { return !positions.isEmpty(); }

    /** Returns the canonical position label matching the value (ignoring case, accents, spaces and symbols). */
    public Optional<String> resolvePosition(String value) {
        if (value == null) return Optional.empty();
        String key = key(value);
        return positions.stream().filter(p -> key(p).equals(key)).findFirst();
    }

    private static String key(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
