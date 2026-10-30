import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assignmentChanges, filterHierarchy, leaderOptions, supervisorIds, toggleIds } from './hierarchyData.js';

const member = (candidateId, fullName, extra = {}) => ({ candidateId, fullName, matricule: `M${candidateId}`, position: 'Couturière', shift: 'EQUIPE_A', teamLeaderId: 9, ...extra });
const tree = [
  {
    name: 'Sup One',
    teamLeaders: [{ userId: 9, fullName: 'Leader Nine', matricule: 'L9', candidateId: 90, assignable: true },
      { userId: 8, fullName: 'Leader Eight', matricule: 'L8', candidateId: null, assignable: false }],
    lines: [
      { line: 'Audi', members: [member(1, 'Amel'), member(2, 'Bochra', { teamLeaderId: null })] },
      { line: 'P6 AV', members: [member(3, 'Chaima', { shift: null })] },
    ],
  },
  { name: null, teamLeaders: [], lines: [{ line: null, members: [member(4, 'Dora', { teamLeaderId: 7 })] }] },
];

test('filters members by search, missing leader and missing shift', () => {
  assert.deepEqual(filterHierarchy(tree, { search: 'bochra' }).map((s) => s.shown), [1]);
  assert.deepEqual(filterHierarchy(tree, { search: 'sup one' })[0].shown, 3);
  assert.deepEqual(filterHierarchy(tree, { search: 'p6' })[0].lines.map((l) => l.line), ['P6 AV']);
  assert.deepEqual(filterHierarchy(tree, { search: 'leader nine' })[0].lines.flatMap((l) => l.members.map((m) => m.fullName)), ['Amel', 'Chaima']);
  assert.deepEqual(filterHierarchy(tree, { missingLeader: true }).flatMap((s) => s.lines.flatMap((l) => l.members.map((m) => m.candidateId))), [2]);
  assert.deepEqual(filterHierarchy(tree, { missingShift: true }).flatMap((s) => s.lines.flatMap((l) => l.members.map((m) => m.candidateId))), [3]);
  assert.equal(filterHierarchy(tree).length, 2);
  assert.equal(filterHierarchy(tree, { search: 'nobody' }).length, 0);
});

test('selects whole branches with team leaders first and toggles them', () => {
  assert.deepEqual(supervisorIds(tree[0]), [90, 1, 2, 3]);
  const selected = toggleIds(new Set([1]), supervisorIds(tree[0]));
  assert.deepEqual([...selected].sort(), [1, 2, 3, 90]);
  assert.equal(toggleIds(selected, [1, 2]).size, 2);
});

test('puts the team leaders of the selected supervisors first', () => {
  const all = [{ id: 7, fullName: 'Other' }, { id: 9, fullName: 'Leader Nine' }];
  const options = leaderOptions(tree, new Set([2]), all);
  assert.deepEqual(options.nearby.map((l) => l.userId), [9]);
  assert.deepEqual(options.others.map((l) => l.id), [7]);
  assert.deepEqual(leaderOptions(tree, new Set(), all).nearby, []);
});

test('builds the assignment request', () => {
  assert.deepEqual(assignmentChanges('EQUIPE_B', ''), { changeShift: true, shift: 'EQUIPE_B' });
  assert.deepEqual(assignmentChanges('NONE', '12'), { changeShift: true, shift: null, changeTeamLeader: true, teamLeaderId: 12 });
  assert.deepEqual(assignmentChanges('', 'NONE'), { changeTeamLeader: true, teamLeaderId: null });
  assert.deepEqual(assignmentChanges('', ''), {});
});
