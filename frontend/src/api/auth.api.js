import client from './client';

export const authApi = {
  login: (username, password) => client.post('/auth/login', { username, password }).then((r) => r.data),
  me: () => client.get('/auth/me').then((r) => r.data),
  changePassword: (currentPassword, newPassword) =>
    client.post('/auth/change-password', { currentPassword, newPassword }).then((r) => r.data),
};
