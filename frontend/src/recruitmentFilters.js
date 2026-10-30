export const NONE = 'NONE';
export const DEFAULT_FILTERS = {
  search: '', status: '', position: '', department: '', supervisor: '', line: '', team: '', shift: '',
  teamLeader: '', trainer: '', level: '', origin: '', hiredFrom: '', hiredTo: '', sort: 'NAME_ASC',
};
export const SORTS = ['NAME_ASC', 'NAME_DESC', 'MATRICULE', 'HIRED_DESC', 'HIRED_ASC'];

/** Case- and accent-insensitive key, so « prép CAMPO » and « Prép CAMPO » are one filter value. */
export const valueKey = (value) => (value == null ? '' : String(value)).normalize('NFD').replace(/\p{M}/gu, '')
  .trim().replace(/\s+/g, ' ').toUpperCase();

/** candidateId → { supervisor, line, team } from the hierarchy endpoint (HR file columns I, H, J). */
export function hrIndex(hierarchy) {
  const index = new Map();
  (hierarchy?.supervisors || []).forEach((s) => {
    s.teamLeaders.forEach((l) => { if (l.candidateId) index.set(l.candidateId, { supervisor: s.name, line: l.line, team: l.team }); });
    s.lines.forEach((line) => line.members.forEach((m) => index.set(m.candidateId, { supervisor: s.name, line: line.line, team: m.team })));
  });
  return index;
}

/** Facet name → value of a candidate; '' means "not filled" (shown as NONE). */
export const FACETS = {
  status: (c) => c.status,
  position: (c) => valueKey(c.position),
  department: (c) => valueKey(c.department),
  supervisor: (c) => valueKey(c.hr?.supervisor),
  line: (c) => valueKey(c.hr?.line),
  team: (c) => valueKey(c.hr?.team),
  shift: (c) => c.shift || '',
  teamLeader: (c) => (c.teamLeader?.id != null ? String(c.teamLeader.id) : ''),
  trainer: (c) => (c.trainer?.id != null ? String(c.trainer.id) : ''),
  level: (c) => c.trainingLevel || '',
  origin: (c) => (c.formationValidatedOn ? 'FORMATION' : 'HR'),
};

function matchesFacet(c, facet, wanted) {
  if (!wanted) return true;
  const value = FACETS[facet](c);
  return wanted === NONE ? value === '' : value === wanted;
}

function matchesSearch(c, query) {
  return !query || [c.fullName, c.cin, c.matricule, c.position, c.department, c.phone, c.hr?.supervisor, c.hr?.line,
    c.teamLeader?.fullName, c.trainer?.fullName].some((v) => valueKey(v).includes(query));
}

function matchesHired(c, from, to) {
  if (!from && !to) return true;
  if (!c.hireDate) return false;
  return (!from || c.hireDate >= from) && (!to || c.hireDate <= to);
}

function matchesAll(c, filters, query, except) {
  return Object.keys(FACETS).every((facet) => facet === except || matchesFacet(c, facet, filters[facet]))
    && matchesSearch(c, query) && matchesHired(c, filters.hiredFrom, filters.hiredTo);
}

const byName = (a, b) => (a.fullName || '').localeCompare(b.fullName || '', undefined, { sensitivity: 'base' });
const COMPARATORS = {
  NAME_ASC: byName,
  NAME_DESC: (a, b) => byName(b, a),
  MATRICULE: (a, b) => (a.matricule || '').localeCompare(b.matricule || '', undefined, { numeric: true }) || byName(a, b),
  // Dossiers without a hire date go last.
  HIRED_DESC: (a, b) => (b.hireDate || '').localeCompare(a.hireDate || '') || byName(a, b),
  HIRED_ASC: (a, b) => (a.hireDate || '9999').localeCompare(b.hireDate || '9999') || byName(a, b),
};

export function filterCandidates(candidates, filters) {
  const query = valueKey(filters.search);
  return candidates.filter((c) => matchesAll(c, filters, query)).sort(COMPARATORS[filters.sort] || byName);
}

/**
 * Options of a facet with counts computed with all the other filters applied.
 * label(c) gives the display text of the candidate's value; the most frequent spelling wins.
 */
export function facetOptions(candidates, filters, facet, label = (c) => FACETS[facet](c)) {
  const query = valueKey(filters.search);
  const options = new Map();
  let none = 0;
  candidates.forEach((c) => {
    if (!matchesAll(c, filters, query, facet)) return;
    const value = FACETS[facet](c);
    if (value === '') { none += 1; return; }
    const option = options.get(value) || { value, count: 0, labels: new Map() };
    option.count += 1;
    const text = label(c);
    option.labels.set(text, (option.labels.get(text) || 0) + 1);
    options.set(value, option);
  });
  const list = [...options.values()].map(({ value, count, labels }) => ({
    value, count, label: [...labels.entries()].sort((a, b) => b[1] - a[1])[0][0],
  })).sort((a, b) => b.count - a.count || String(a.label).localeCompare(String(b.label)));
  return { options: list, none };
}

export const activeFilters = (filters) => Object.keys(DEFAULT_FILTERS)
  .filter((key) => key !== 'sort' && key !== 'search' && filters[key] !== DEFAULT_FILTERS[key]);

export const isFiltered = (filters) => Boolean(filters.search.trim()) || activeFilters(filters).length > 0;
