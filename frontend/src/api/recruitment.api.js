import client from './client';
import downloadExcel from './downloadExcel';

export const recruitmentApi = {
  list: (category) => client.get('/recruitment/candidates', { params: { category } }).then((r) => r.data),
  staff: () => client.get('/recruitment/staff').then((r) => r.data),
  create: (data) => client.post('/recruitment/candidates', data).then((r) => r.data),
  update: (id, data) => client.put(`/recruitment/candidates/${id}`, data).then((r) => r.data),
  setStatus: (id, status, position) => client.patch(`/recruitment/candidates/${id}/status`, { status, position }).then((r) => r.data),
  getBooklet: (id) => client.get(`/recruitment/candidates/${id}/booklet`).then((r) => r.data),
  updateBooklet: (id, data) => client.put(`/recruitment/candidates/${id}/booklet`, data).then((r) => r.data),
  signCandidateBooklet: (id, signature) => client.post(`/recruitment/candidates/${id}/booklet/candidate-signature`, { signature }).then((r) => r.data),
  signTrainerBooklet: (id) => client.post(`/recruitment/candidates/${id}/booklet/trainer-signature`).then((r) => r.data),
  getTrainingSheet: (id) => client.get(`/training-sheets/candidates/${id}`).then((r) => r.data),
  updateTrainingSheet: (id, data) => client.put(`/training-sheets/candidates/${id}`, data).then((r) => r.data),
  signTrainingPeriodTrainer: (candidateId, periodId) => client.post(`/training-sheets/candidates/${candidateId}/periods/${periodId}/trainer-signature`).then((r) => r.data),
  signTrainingPeriodLeader: (candidateId, periodId) => client.post(`/training-sheets/candidates/${candidateId}/periods/${periodId}/team-leader-signature`).then((r) => r.data),
  signTrainingSheetSupervisor: (id) => client.post(`/training-sheets/candidates/${id}/supervisor-signature`).then((r) => r.data),
  signTrainingSheetQuality: (id) => client.post(`/training-sheets/candidates/${id}/quality-signature`).then((r) => r.data),
  getWorkstationForm: (id) => client.get(`/workstation-forms/candidates/${id}`).then((r) => r.data),
  updateWorkstationForm: (id, data) => client.put(`/workstation-forms/candidates/${id}`, data).then((r) => r.data),
  signWorkstationStep: (id, kind, stepId, version) => client.post(`/workstation-forms/candidates/${id}/${kind}/steps/${stepId}/signature`, { version }).then((r) => r.data),
  signWorkstationOperator: (id, kind, version, signature) => client.post(`/workstation-forms/candidates/${id}/${kind}/operator-signature`, { version, signature }).then((r) => r.data),
  remove: (id) => client.delete(`/recruitment/candidates/${id}`),
  positions: () => client.get('/recruitment/positions').then((r) => r.data),
  exportExcel: (category) => downloadExcel('/recruitment/excel/export', { category }),
  template: (category) => downloadExcel('/recruitment/excel/template', { category }),
  importExcel: (category, file) => {
    const data = new FormData();
    data.append('file', file);
    return client.post('/recruitment/excel/import', data, { params: { category } }).then((r) => r.data);
  },
};
