import client from './client';

export const usersApi = {
  list: () => client.get('/users').then((r) => r.data),
  roles: () => client.get('/users/roles').then((r) => r.data),
  create: (data) => client.post('/users', data).then((r) => r.data),
  update: (id, data) => client.put(`/users/${id}`, data).then((r) => r.data),
  setStatus: (id, active) => client.patch(`/users/${id}/status`, { active }).then((r) => r.data),
  resetPassword: (id) => client.post(`/users/${id}/reset-password`).then((r) => r.data),
  remove: (id) => client.delete(`/users/${id}`),
};
