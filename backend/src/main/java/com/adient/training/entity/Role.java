package com.adient.training.entity;

import java.util.Set;

public enum Role {
    SUPER_ADMIN("Super Admin", 5),
    ADMIN("Admin", 4),
    SUPERVISEUR("Superviseur", 3),
    FORMATRICE("Formatrice", 2),
    TEAM_LEADER("Team Leader", 1),
    QUALITY("Qualité", 1),
    JOB_EMPLOYEE("Employé", 0),
    JOB_ACHAT_GENERAUX("Achat Généraux", 0),
    JOB_AGENT_SECURITE("Agent sécurité", 0),
    JOB_AIDE_SOIGNANTE("Aide soignante", 0),
    JOB_ALIMENTATRICE("Alimentatrice", 0),
    JOB_APPROVISIONNEUR("Approvisionneur", 0),
    JOB_AUDITEUR_QUALITE("Auditeur Qualité", 0),
    JOB_CAMPO("CAMPO", 0),
    JOB_CHEF_PROJET("CHEF DE PROJET", 0),
    JOB_COORD_PRODUCTION("COORDINATEUR PRODUCTION", 0),
    JOB_COORD_LOGISTIQUE("COORDINATRICE LOGISTIQUE", 0),
    JOB_COUPEUR("COUPEUR", 0),
    JOB_CSL("CSL", 0),
    JOB_CONTROLE_QUALITE("Contrôle Qualité", 0),
    JOB_CONTROLE_QUALITE_COUPE("Contrôle Qualité coupe", 0),
    JOB_COUTURIERE("Couturière", 0),
    JOB_FORMATRICE("FORMATRICE", 0),
    JOB_FEMME_MENAGE("Femme de menage", 0),
    JOB_FIRE_W("Fire W", 0),
    JOB_HR_MANAGER("HR MANAGER", 0),
    JOB_INGENIEUR_PROCESS("INGENIEUR PROCESS", 0),
    JOB_INGENIEUR_QUALITE("INGENIEUR QUALITE", 0),
    JOB_ING_QUALITE_PROJET_CLIENT("INGENIEUR QUALITE PROJET/ CLIENT", 0),
    JOB_ING_QUALITE_SYSTEM_FOURN("INGENIEUR QUALITE SYSTEM & FOURNISSUER", 0),
    JOB_INVENTORISTE("INVENTORISTE", 0),
    JOB_JIT_OPERATOR("JIT Operator", 0),
    JOB_MAGASINIER_PDR("MAGASINIER PDR", 0),
    JOB_MANAGER_QUALITE("MANAGER QUALITE", 0),
    JOB_MANITENTIONNAIRE("MANITENTIONNAIRE", 0),
    JOB_OPERATION("OPERATION", 0),
    JOB_PLANIFICATEUR("Planificateur", 0),
    JOB_PREP_CAMPO("Prép CAMPO", 0),
    JOB_RESP_INGENIERIE("RESPONSABLE INGENIERIE", 0),
    JOB_RESP_IT_SITE("RESPONSABLE IT SITE", 0),
    JOB_RESP_APPROVISIONNEMENT("RESPONSABLE APPROVISIONNEMENT", 0),
    JOB_RESP_LOGISTIQUE("RESPONSABLE LOGISTIQUE", 0),
    JOB_RESP_MAGASIN("RESPONSABLE MAGASIN", 0),
    JOB_RESP_MAINTENANCE("RESPONSABLE MAINTENANCE", 0),
    JOB_RESP_RH("RESPONSABLE RESSOURCES HUMAINES", 0),
    JOB_SUP_PRODUCTION("SUPERVISEUR DE PRODUCTION", 0),
    JOB_SUP_MAINTENANCE("SUPERVISEUR MAINTENANCE", 0),
    JOB_SPECIALIST_SG("Specialist SG", 0),
    JOB_SPECIALIST_FLUX_COUPE("Specialist flux coupe", 0),
    JOB_SUP_LOGISTIQUE("Superviseur Logistique", 0),
    JOB_TECH_CAO_METHODES("TECHNICIEN CAO & Méthodes", 0),
    JOB_TECH_MACHINE_COUDRE("TECHNICIEN MACHINE A COUDRE", 0),
    JOB_TECH_MAINTENANCE_COUPE("TECHNICIEN MAINTENANCE COUPE", 0),
    JOB_TEAM_LEADER("Team Leader", 0),
    JOB_TECH_AMELIORATION("Technicien Amélioration Continue", 0),
    JOB_TECH_CAO("Technicien CAO", 0),
    JOB_TRAINING_SPECIALIST("Training Specialist", 0),
    JOB_WIRE("WIRE", 0);

    private final String label;
    private final int level;

    Role(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String getLabel() { return label; }
    public int getLevel() { return level; }
    public boolean hasModuleAccess() { return level > 0; }

    /** Super Admin manages every role; Admin manages operational accounts except Super Admin. */
    public boolean canManage(Role target) {
        return switch (this) {
            case SUPER_ADMIN -> true;
            case ADMIN -> Set.of(SUPERVISEUR, FORMATRICE, TEAM_LEADER, QUALITY).contains(target);
            default -> false;
        };
    }

    public boolean isUserManager() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
