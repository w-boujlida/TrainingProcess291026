export const STATUS_FILTERS = ['ALL', 'ACTIVE', 'INACTIVE', 'PENDING'];
export const TYPE_FILTERS = ['ALL', 'CADRE', 'DIRECT', 'INDIRECT', 'NONE'];
export const SORTS = ['NAME_ASC', 'NAME_DESC', 'USERNAME', 'LAST_LOGIN', 'RECENT'];
export const DEFAULT_FILTERS = { search: '', role: '', status: 'ALL', type: 'ALL', sort: 'NAME_ASC' };

const text = (value) => (value || '').toString().toLowerCase();

export function matchesStatus(user, status) {
  if (status === 'ACTIVE') return user.active;
  if (status === 'INACTIVE') return !user.active;
  if (status === 'PENDING') return user.mustChangePassword;
  return true;
}

export function matchesType(user, type) {
  if (type === 'NONE') return !user.workType;
  return type === 'ALL' || user.workType === type;
}

function matchesSearch(user, query) {
  return !query || text(user.fullName).includes(query) || text(user.username).includes(query) || text(user.email).includes(query);
}

const byName = (a, b) => (a.fullName || '').localeCompare(b.fullName || '', undefined, { sensitivity: 'base' });
const COMPARATORS = {
  NAME_ASC: byName,
  NAME_DESC: (a, b) => byName(b, a),
  USERNAME: (a, b) => (a.username || '').localeCompare(b.username || '', undefined, { numeric: true, sensitivity: 'base' }),
  // Never-logged-in accounts go last.
  LAST_LOGIN: (a, b) => (b.lastLoginAt || '').localeCompare(a.lastLoginAt || '') || byName(a, b),
  RECENT: (a, b) => (b.id ?? 0) - (a.id ?? 0),
};

export function filterUsers(users, filters) {
  const query = filters.search.trim().toLowerCase();
  return users
    .filter((u) => (!filters.role || u.role === filters.role) && matchesStatus(u, filters.status)
      && matchesType(u, filters.type) && matchesSearch(u, query))
    .sort(COMPARATORS[filters.sort] || byName);
}

/** Counts per quick-filter value, each computed with the other active filters applied. */
export function quickCounts(users, filters) {
  const query = filters.search.trim().toLowerCase();
  const base = users.filter((u) => (!filters.role || u.role === filters.role) && matchesSearch(u, query));
  const status = Object.fromEntries(STATUS_FILTERS.map((s) => [s, 0]));
  const type = Object.fromEntries(TYPE_FILTERS.map((s) => [s, 0]));
  for (const u of base) {
    if (matchesType(u, filters.type)) STATUS_FILTERS.forEach((s) => { if (matchesStatus(u, s)) status[s] += 1; });
    if (matchesStatus(u, filters.status)) TYPE_FILTERS.forEach((s) => { if (matchesType(u, s)) type[s] += 1; });
  }
  return { status, type };
}

export function isFiltered(filters) {
  return Object.keys(DEFAULT_FILTERS).some((key) => key !== 'sort' && filters[key] !== DEFAULT_FILTERS[key]);
}
