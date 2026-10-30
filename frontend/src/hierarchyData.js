export const NO_SHIFT = 'NONE';
export const NO_LEADER = 'NONE';

const text = (value) => (value || '').toLowerCase();

function memberMatches(member, query, { missingLeader, missingShift }) {
  if (missingLeader && member.teamLeaderId) return false;
  if (missingShift && member.shift) return false;
  return !query || [member.fullName, member.matricule, member.position].some((v) => text(v).includes(query));
}

/**
 * Keeps the members matching the filters. A search on a supervisor or line name keeps the whole group;
 * a search on a team leader keeps the members they lead.
 */
export function filterHierarchy(supervisors, { search = '', missingLeader = false, missingShift = false } = {}) {
  const query = search.trim().toLowerCase();
  const flags = { missingLeader, missingShift };
  return supervisors.map((supervisor) => {
    const wholeSupervisor = query && text(supervisor.name).includes(query);
    const leaderIds = new Set(supervisor.teamLeaders
      .filter((l) => query && [l.fullName, l.matricule].some((v) => text(v).includes(query))).map((l) => l.userId));
    const lines = supervisor.lines.map((line) => {
      const wholeLine = wholeSupervisor || (query && text(line.line).includes(query));
      const members = line.members.filter((m) => (wholeLine || leaderIds.has(m.teamLeaderId)
        ? memberMatches(m, '', flags) : memberMatches(m, query, flags)));
      return { ...line, members };
    }).filter((line) => line.members.length > 0);
    return { ...supervisor, lines, shown: lines.reduce((sum, line) => sum + line.members.length, 0) };
  }).filter((supervisor) => supervisor.shown > 0 || (!missingLeader && !missingShift && query
    && (text(supervisor.name).includes(query) || supervisor.teamLeaders.some((l) => [l.fullName, l.matricule].some((v) => text(v).includes(query))))));
}

export const lineIds = (line) => line.members.map((m) => m.candidateId);

/** Dossiers of a whole branch: its team leaders first (supervisor → team leader → employees), then the employees. */
export function supervisorIds(supervisor) {
  return [...supervisor.teamLeaders.filter((l) => l.candidateId).map((l) => l.candidateId), ...supervisor.lines.flatMap(lineIds)];
}

export const allSelected = (ids, selected) => ids.length > 0 && ids.every((id) => selected.has(id));

export function toggleIds(selected, ids) {
  const next = new Set(selected);
  if (allSelected(ids, selected)) ids.forEach((id) => next.delete(id));
  else ids.forEach((id) => next.add(id));
  return next;
}

/** Team leaders of the supervisors touched by the selection come first in the picker. */
export function leaderOptions(supervisors, selected, allLeaders) {
  const near = new Map();
  supervisors.forEach((supervisor) => {
    if (!supervisorIds(supervisor).some((id) => selected.has(id))) return;
    supervisor.teamLeaders.filter((l) => l.assignable).forEach((l) => near.set(l.userId, l));
  });
  const nearby = [...near.values()].sort((a, b) => (a.fullName || '').localeCompare(b.fullName || ''));
  return { nearby, others: allLeaders.filter((l) => !near.has(l.id)) };
}

/** Request body for the bulk assignment; an empty choice leaves the field unchanged. */
export function assignmentChanges(shift, leader) {
  const changes = {};
  if (shift) Object.assign(changes, { changeShift: true, shift: shift === NO_SHIFT ? null : shift });
  if (leader) Object.assign(changes, { changeTeamLeader: true, teamLeaderId: leader === NO_LEADER ? null : Number(leader) });
  return changes;
}
