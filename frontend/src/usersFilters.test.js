import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_FILTERS, filterUsers, isFiltered, quickCounts } from './usersFilters.js';

const users = [
  { id: 1, fullName: 'Zied', username: '0010', role: 'ADMIN', active: true, mustChangePassword: false, workType: null, lastLoginAt: '2026-10-01T08:00:00' },
  { id: 2, fullName: 'amal', username: '0002', role: 'JOB_EMPLOYEE', active: true, mustChangePassword: true, workType: 'DIRECT', lastLoginAt: null },
  { id: 3, fullName: 'Béchir', username: '0100', role: 'JOB_EMPLOYEE', active: false, mustChangePassword: true, workType: 'CADRE', lastLoginAt: '2026-10-20T08:00:00' },
  { id: 4, fullName: 'Chiraz', username: '0003', role: 'JOB_EMPLOYEE', active: true, mustChangePassword: false, workType: 'DIRECT', email: 'c@x.tn' },
];
const ids = (list) => list.map((u) => u.id);
const f = (patch) => ({ ...DEFAULT_FILTERS, ...patch });

test('filters by status, work type, role and search together', () => {
  assert.deepEqual(ids(filterUsers(users, f({ status: 'ACTIVE', type: 'DIRECT' }))), [2, 4]);
  assert.deepEqual(ids(filterUsers(users, f({ status: 'INACTIVE' }))), [3]);
  assert.deepEqual(ids(filterUsers(users, f({ status: 'PENDING' }))), [2, 3]);
  assert.deepEqual(ids(filterUsers(users, f({ type: 'NONE' }))), [1]);
  assert.deepEqual(ids(filterUsers(users, f({ role: 'ADMIN' }))), [1]);
  assert.deepEqual(ids(filterUsers(users, f({ search: ' C@X ' }))), [4]);
});

test('sorts without mutating the source list', () => {
  const copy = [...users];
  assert.deepEqual(ids(filterUsers(users, f({}))), [2, 3, 4, 1]);
  assert.deepEqual(ids(filterUsers(users, f({ sort: 'NAME_DESC' }))), [1, 4, 3, 2]);
  assert.deepEqual(ids(filterUsers(users, f({ sort: 'USERNAME' }))), [2, 4, 1, 3]);
  assert.deepEqual(ids(filterUsers(users, f({ sort: 'LAST_LOGIN' }))), [3, 1, 2, 4]);
  assert.deepEqual(ids(filterUsers(users, f({ sort: 'RECENT' }))), [4, 3, 2, 1]);
  assert.deepEqual(users, copy);
});

test('quick counts apply the other filters', () => {
  const counts = quickCounts(users, f({ type: 'DIRECT' }));
  assert.deepEqual(counts.status, { ALL: 2, ACTIVE: 2, INACTIVE: 0, PENDING: 1 });
  assert.deepEqual(counts.type, { ALL: 4, CADRE: 1, DIRECT: 2, INDIRECT: 0, NONE: 1 });
  assert.deepEqual(quickCounts(users, f({ status: 'ACTIVE' })).type, { ALL: 3, CADRE: 0, DIRECT: 2, INDIRECT: 0, NONE: 1 });
});

test('detects active filters but ignores sort', () => {
  assert.equal(isFiltered(f({ sort: 'NAME_DESC' })), false);
  assert.equal(isFiltered(f({ type: 'CADRE' })), true);
});
