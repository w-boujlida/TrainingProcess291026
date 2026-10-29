import client from './client';

function form(file, confirmation) {
  const data = new FormData();
  data.append('file', file);
  if (confirmation) data.append('confirmation', confirmation);
  return data;
}
export const hrImportApi = {
  preview: (file) => client.post('/users/hr-import/preview', form(file), { timeout: 600000 }).then((r) => r.data),
  apply: (file, confirmation) => client.post('/users/hr-import/apply', form(file, confirmation), { timeout: 600000 }).then((r) => r.data),
};
