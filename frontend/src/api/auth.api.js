import client from './client';

export const authApi = {
  login: (username, password, deviceName) => client.post('/auth/login', { username, password, deviceName }).then((r) => r.data),
  logout: () => client.post('/auth/logout'),
  me: () => client.get('/auth/me').then((r) => r.data),
  changePassword: (currentPassword, newPassword) =>
    client.post('/auth/change-password', { currentPassword, newPassword }).then((r) => r.data),
};
