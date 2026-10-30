import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_FILTERS, NONE, activeFilters, facetOptions, filterCandidates, hrIndex, isFiltered, valueKey } from './recruitmentFilters.js';

const c = (id, fullName, extra = {}) => ({ id, fullName, matricule: `M${id}`, status: 'EN_FORMATION', ...extra });
const list = [
  c(1, 'Amel', { position: 'Couturière', department: 'PRODUCTION', shift: 'EQUIPE_A', hireDate: '2024-03-01', teamLeader: { id: 9, fullName: 'Leila' }, hr: { supervisor: 'Sup X', line: 'Audi', team: 'A' } }),
  c(2, 'Bochra', { position: 'COUTURIERE ', department: 'Production', shift: 'EQUIPE_B', hireDate: '2025-01-10', hr: { supervisor: 'SUP X', line: 'P6 AV', team: 'B' } }),
  c(3, 'Chaima', { position: 'prép CAMPO', department: 'COUPE', hireDate: '2023-06-15', formationValidatedOn: '2023-07-01' }),
  c(4, 'Dora', { position: 'Prép CAMPO', status: 'VALIDE', shift: 'EQUIPE_A' }),
];
const f = (extra) => ({ ...DEFAULT_FILTERS, ...extra });
const names = (rows) => rows.map((r) => r.fullName);

test('normalises spelling variants into one value', () => {
  assert.equal(valueKey(' prép  Campo '), 'PREP CAMPO');
  assert.equal(valueKey(null), '');
});

test('applies every facet, missing values, dates and search together', () => {
  assert.deepEqual(names(filterCandidates(list, f({ position: 'COUTURIERE' }))), ['Amel', 'Bochra']);
  assert.deepEqual(names(filterCandidates(list, f({ position: 'PREP CAMPO', status: 'VALIDE' }))), ['Dora']);
  assert.deepEqual(names(filterCandidates(list, f({ supervisor: 'SUP X', line: 'P6 AV' }))), ['Bochra']);
  assert.deepEqual(names(filterCandidates(list, f({ shift: NONE }))), ['Chaima']);
  assert.deepEqual(names(filterCandidates(list, f({ teamLeader: NONE, department: 'PRODUCTION' }))), ['Bochra']);
  assert.deepEqual(names(filterCandidates(list, f({ teamLeader: '9' }))), ['Amel']);
  assert.deepEqual(names(filterCandidates(list, f({ origin: 'FORMATION' }))), ['Chaima']);
  assert.deepEqual(names(filterCandidates(list, f({ hiredFrom: '2024-01-01' }))), ['Amel', 'Bochra']);
  assert.deepEqual(names(filterCandidates(list, f({ hiredTo: '2024-12-31' }))), ['Amel', 'Chaima']);
  assert.deepEqual(names(filterCandidates(list, f({ search: 'leila' }))), ['Amel']);
  assert.deepEqual(names(filterCandidates(list, f({ search: 'p6 av' }))), ['Bochra']);
});

test('sorts by name, matricule and hire date', () => {
  assert.deepEqual(names(filterCandidates(list, f({ sort: 'NAME_DESC' }))), ['Dora', 'Chaima', 'Bochra', 'Amel']);
  assert.deepEqual(names(filterCandidates(list, f({ sort: 'HIRED_DESC' }))), ['Bochra', 'Amel', 'Chaima', 'Dora']);
  assert.deepEqual(names(filterCandidates(list, f({ sort: 'HIRED_ASC' }))), ['Chaima', 'Amel', 'Bochra', 'Dora']);
});

test('facet counts follow the other filters and keep the most frequent spelling', () => {
  const positions = facetOptions(list, f(), 'position', (x) => x.position.trim());
  assert.deepEqual(positions.options.map((o) => [o.value, o.count]), [['COUTURIERE', 2], ['PREP CAMPO', 2]]);
  assert.ok(['Couturière', 'COUTURIERE'].includes(positions.options[0].label));
  const shifts = facetOptions(list, f({ position: 'PREP CAMPO', shift: 'EQUIPE_B' }), 'shift');
  assert.deepEqual(shifts.options.map((o) => [o.value, o.count]), [['EQUIPE_A', 1]]);
  assert.equal(shifts.none, 1);
});

test('indexes HR supervisor, line and team per dossier', () => {
  const index = hrIndex({ supervisors: [{ name: 'Sup X', teamLeaders: [{ candidateId: 7, line: 'Audi', team: 'A' }, { candidateId: null }],
    lines: [{ line: 'Audi', members: [{ candidateId: 1, team: 'A' }] }] }] });
  assert.deepEqual(index.get(1), { supervisor: 'Sup X', line: 'Audi', team: 'A' });
  assert.deepEqual(index.get(7), { supervisor: 'Sup X', line: 'Audi', team: 'A' });
  assert.equal(index.size, 2);
});

test('lists active filters without search and sort', () => {
  assert.deepEqual(activeFilters(f({ search: 'x', sort: 'NAME_DESC', line: 'AUDI', hiredFrom: '2024-01-01' })), ['line', 'hiredFrom']);
  assert.equal(isFiltered(f()), false);
  assert.equal(isFiltered(f({ search: ' a ' })), true);
});
