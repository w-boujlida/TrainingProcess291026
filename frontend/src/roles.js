export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  SUPERVISEUR: 'SUPERVISEUR',
  FORMATRICE: 'FORMATRICE',
  TEAM_LEADER: 'TEAM_LEADER',
};

export const ROLE_LABELS = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  SUPERVISEUR: 'Superviseur',
  FORMATRICE: 'Formatrice',
  TEAM_LEADER: 'Team Leader',
};

export const USER_MANAGERS = [ROLES.SUPER_ADMIN, ROLES.ADMIN];

export const RECRUITMENT_EDITORS = [ROLES.SUPER_ADMIN, ROLES.ADMIN, ROLES.FORMATRICE];

export const RECRUITMENT_CATEGORIES = [
  { code: 'CADRE', slug: 'cadre', label: 'Cadre', icon: 'briefcase' },
  { code: 'DIRECT', slug: 'direct', label: 'Direct', icon: 'tool' },
  { code: 'INDIRECT', slug: 'indirect', label: 'Indirect', icon: 'layers' },
  { code: 'EXTERNAL', slug: 'external', label: 'External', icon: 'globe' },
];

export const CANDIDATE_STATUSES = [
  { code: 'CANDIDAT', label: 'Candidat' },
  { code: 'RECRUTE', label: 'Recruté' },
  { code: 'EN_FORMATION', label: 'En formation' },
  { code: 'VALIDE', label: 'Validé' },
  { code: 'REJETE', label: 'Rejeté' },
];

export const SHIFTS = [
  { code: 'MATIN', label: 'Matin' },
  { code: 'APRES_MIDI', label: 'Après-midi' },
  { code: 'NUIT', label: 'Nuit' },
  { code: 'NORMAL', label: 'Normal' },
];

/** Mirrors backend Role.canManage. */
export function canManage(actorRole, targetRole) {
  if (actorRole === ROLES.SUPER_ADMIN) return true;
  if (actorRole === ROLES.ADMIN) {
    return [ROLES.SUPERVISEUR, ROLES.FORMATRICE, ROLES.TEAM_LEADER].includes(targetRole);
  }
  return false;
}
