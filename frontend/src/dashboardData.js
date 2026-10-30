const LABELS = {
  NON_RENSEIGNE: 'Non renseigné',
  AUTRES: 'Autres',
  CADRE: 'Cadre',
  DIRECT: 'Direct',
  INDIRECT: 'Indirect',
  FORMATION: 'Formation',
  FEMME: 'Femme',
  HOMME: 'Homme',
  PENDING: 'En attente',
  ACQUIRED: 'Acquis',
  TO_REINFORCE: 'À renforcer',
  OK: 'Conforme (OK)',
  NOK: 'Non conforme (NOK)',
  NA: 'Non applicable',
  '< 25': '< 25 ans',
  '>25< 35': '25 – 35 ans',
  '>35< 45': '35 – 45 ans',
  '>45< 55': '45 – 55 ans',
  '> 55': '> 55 ans',
  '< 1': '< 1 an',
  '< 2': '1 – 2 ans',
  '< 3': '2 – 3 ans',
  '< 4': '3 – 4 ans',
  '> 4': '> 4 ans',
};

/** French label for an aggregate key; free-text values (departments, lines, contracts) are kept as is. */
export const bucketLabel = (key) => LABELS[key] || key;

export const percent = (part, total) => (total > 0 ? Math.round((part * 100) / total) : 0);

export const total = (buckets = []) => buckets.reduce((sum, b) => sum + b.count, 0);

/** Pivot [{category,status,count}] into { category: { status: count, total } }. */
export function candidateMatrix(rows = []) {
  const matrix = {};
  for (const { category, status, count } of rows) {
    matrix[category] ||= { total: 0 };
    matrix[category][status] = (matrix[category][status] || 0) + count;
    matrix[category].total += count;
  }
  return matrix;
}

/**
 * Project × position table: the `top` most frequent positions get a column, the rest is summed in AUTRES.
 * Returns { columns, rows: [{ project, lines, cells: {position: count}, total }] }.
 */
export function projectMatrix(projects = [], positions = [], top = 6) {
  const columns = positions.filter((p) => p.key !== 'NON_RENSEIGNE').slice(0, top).map((p) => p.key);
  const hasOthers = positions.some((p) => !columns.includes(p.key));
  const rows = projects.map((p) => {
    const cells = Object.fromEntries(columns.map((c) => [c, 0]));
    if (hasOthers) cells.AUTRES = 0;
    p.positions.forEach((b) => { if (b.key in cells && b.key !== 'AUTRES') cells[b.key] += b.count; else if (hasOthers) cells.AUTRES += b.count; });
    return { project: p.project, lines: p.lines, seamstresses: p.seamstresses, cells, total: p.count };
  });
  return { columns: hasOthers ? [...columns, 'AUTRES'] : columns, rows };
}
