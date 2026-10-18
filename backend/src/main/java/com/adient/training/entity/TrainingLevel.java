package com.adient.training.entity;

import java.time.LocalDate;
import java.time.Period;

/** Level of a "Formation" employee; each level has its own trial period. */
public enum TrainingLevel {
    APPRENTIE("Apprentie", "1 mois et demi", Period.ofMonths(1).plusDays(15)),
    JUNIOR("Junior", "2 à 3 semaines", Period.ofWeeks(3)),
    CONFIRMEE("Confirmée", "15 jours", Period.ofDays(15));

    private final String label;
    private final String trialLabel;
    private final Period trial;

    TrainingLevel(String label, String trialLabel, Period trial) {
        this.label = label;
        this.trialLabel = trialLabel;
        this.trial = trial;
    }

    public String getLabel() { return label; }

    public String getTrialLabel() { return trialLabel; }

    /** End of the trial period (upper bound for Junior), or null when the start date is unknown. */
    public LocalDate trialEnd(LocalDate start) {
        return start == null ? null : start.plus(trial);
    }
}