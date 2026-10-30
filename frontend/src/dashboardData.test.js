import test from 'node:test';
import assert from 'node:assert/strict';
import { bucketLabel, candidateMatrix, percent, projectMatrix, total } from './dashboardData.js';

test('labels known keys and keeps free text', () => {
  assert.equal(bucketLabel('>25< 35'), '25 – 35 ans');
  assert.equal(bucketLabel('NON_RENSEIGNE'), 'Non renseigné');
  assert.equal(bucketLabel('P6 AV'), 'P6 AV');
});

test('computes totals and safe percentages', () => {
  assert.equal(total([{ key: 'a', count: 2 }, { key: 'b', count: 3 }]), 5);
  assert.equal(total(), 0);
  assert.equal(percent(1, 3), 33);
  assert.equal(percent(5, 0), 0);
});

test('pivots candidates by category and status', () => {
  const m = candidateMatrix([
    { category: 'DIRECT', status: 'EN_FORMATION', count: 4 },
    { category: 'DIRECT', status: 'VALIDE', count: 1 },
    { category: 'CADRE', status: 'CANDIDAT', count: 2 },
  ]);
  assert.deepEqual(m, { DIRECT: { total: 5, EN_FORMATION: 4, VALIDE: 1 }, CADRE: { total: 2, CANDIDAT: 2 } });
});

test('builds the project x position table with an Autres column', () => {
  const positions = [{ key: 'Couturière', count: 5 }, { key: 'COUPEUR', count: 2 }, { key: 'WIRE', count: 1 }, { key: 'NON_RENSEIGNE', count: 1 }];
  const projects = [
    { project: 'P6', count: 4, seamstresses: 3, lines: [], positions: [{ key: 'Couturière', count: 3 }, { key: 'WIRE', count: 1 }] },
    { project: 'NON_RENSEIGNE', count: 5, seamstresses: 2, lines: [], positions: [{ key: 'Couturière', count: 2 }, { key: 'COUPEUR', count: 2 }, { key: 'NON_RENSEIGNE', count: 1 }] },
  ];
  const m = projectMatrix(projects, positions, 2);
  assert.deepEqual(m.columns, ['Couturière', 'COUPEUR', 'AUTRES']);
  assert.deepEqual(m.rows[0].cells, { 'Couturière': 3, COUPEUR: 0, AUTRES: 1 });
  assert.deepEqual(m.rows[1].cells, { 'Couturière': 2, COUPEUR: 2, AUTRES: 1 });
  assert.equal(m.rows[1].total, 5);
  assert.deepEqual(projectMatrix(projects.slice(0, 1), positions.slice(0, 1)).columns, ['Couturière']);
});
