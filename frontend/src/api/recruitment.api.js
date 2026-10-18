import client from './client';

export const recruitmentApi = {
  list: (category) => client.get('/recruitment/candidates', { params: { category } }).then((r) => r.data),
  staff: () => client.get('/recruitment/staff').then((r) => r.data),
  create: (data) => client.post('/recruitment/candidates', data).then((r) => r.data),
  update: (id, data) => client.put(`/recruitment/candidates/${id}`, data).then((r) => r.data),
  setStatus: (id, status) => client.patch(`/recruitment/candidates/${id}/status`, { status }).then((r) => r.data),
  remove: (id) => client.delete(`/recruitment/candidates/${id}`),
  positions: () => client.get('/recruitment/positions').then((r) => r.data),
  exportExcel: (category) => download('/recruitment/excel/export', category),
  template: (category) => download('/recruitment/excel/template', category),
  importExcel: (category, file) => {
    const data = new FormData();
    data.append('file', file);
    return client.post('/recruitment/excel/import', data, { params: { category } }).then((r) => r.data);
  },
};

async function download(url, category) {
  let res;
  try {
    res = await client.get(url, { params: { category }, responseType: 'blob' });
  } catch (err) {
    if (err.response?.data instanceof Blob) {
      try { err.response.data = JSON.parse(await err.response.data.text()); } catch { /* keep blob */ }
    }
    throw err;
  }
  const disposition = res.headers['content-disposition'] || '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = match ? match[1] : 'export.xlsx';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}
