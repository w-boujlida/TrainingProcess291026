import client from './client';

export default async function downloadExcel(url, params, data) {
  let response;
  try {
    response = data === undefined
      ? await client.get(url, { params, responseType: 'blob' })
      : await client.post(url, data, { responseType: 'blob' });
  } catch (err) {
    if (err.response?.data instanceof Blob) {
      try { err.response.data = JSON.parse(await err.response.data.text()); } catch { /* keep blob */ }
    }
    throw err;
  }
  const disposition = response.headers['content-disposition'] || '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const href = URL.createObjectURL(response.data);
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = match ? match[1] : 'export.xlsx';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(href);
}
