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

/** Mirrors backend Role.canManage. */
export function canManage(actorRole, targetRole) {
  if (actorRole === ROLES.SUPER_ADMIN) return true;
  if (actorRole === ROLES.ADMIN) {
    return [ROLES.SUPERVISEUR, ROLES.FORMATRICE, ROLES.TEAM_LEADER].includes(targetRole);
  }
  return false;
}
