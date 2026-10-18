import client from './client';

export const recruitmentApi = {
  list: (category) => client.get('/recruitment/candidates', { params: { category } }).then((r) => r.data),
  staff: () => client.get('/recruitment/staff').then((r) => r.data),
  create: (data) => client.post('/recruitment/candidates', data).then((r) => r.data),
  update: (id, data) => client.put(`/recruitment/candidates/${id}`, data).then((r) => r.data),
  setStatus: (id, status) => client.patch(`/recruitment/candidates/${id}/status`, { status }).then((r) => r.data),
  remove: (id) => client.delete(`/recruitment/candidates/${id}`),
};
