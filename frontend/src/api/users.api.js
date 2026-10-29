import client from './client';
import downloadExcel from './downloadExcel';

export const usersApi = {
  list: () => client.get('/users').then((r) => r.data),
  roles: () => client.get('/users/roles').then((r) => r.data),
  hrProfile: (id) => client.get(`/users/${id}/hr-profile`).then((r) => r.data),
  create: (data) => client.post('/users', data).then((r) => r.data),
  update: (id, data) => client.put(`/users/${id}`, data).then((r) => r.data),
  setStatus: (id, active) => client.patch(`/users/${id}/status`, { active }).then((r) => r.data),
  resetPassword: (id) => client.post(`/users/${id}/reset-password`).then((r) => r.data),
  remove: (id) => client.delete(`/users/${id}`),
  exportExcel: () => downloadExcel('/users/excel/export'),
  exportSelection: (ids) => downloadExcel('/users/excel/export-selection', undefined, ids),
  applySelection: (data) => client.post('/users/selection', data).then((response) => response.data),
  template: () => downloadExcel('/users/excel/template'),
  importExcel: (file) => {
    const data = new FormData();
    data.append('file', file);
    return client.post('/users/excel/import', data).then((response) => response.data);
  },
};
