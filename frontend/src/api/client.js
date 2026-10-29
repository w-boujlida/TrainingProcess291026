import { t, getLanguage } from '../i18n';
import axios from 'axios';

export const TOKEN_KEY = 'tp_token';

const client = axios.create({ baseURL: '/api' });

client.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = getLanguage();
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

function reportAuditFailure(response) {
  if (response?.headers?.['x-audit-status'] === 'failed') {
    sessionStorage.setItem('tp_audit_failure', 'true');
    window.dispatchEvent(new Event('audit-write-failed'));
  }
}

client.interceptors.response.use(
  (res) => { reportAuditFailure(res); return res; },
  (error) => {
    reportAuditFailure(error.response);
    const status = error.response?.status;
    const isLogin = error.config?.url?.includes('/auth/login');
    if (status === 401 && !isLogin) {
      localStorage.removeItem(TOKEN_KEY);
      window.location.assign('/login');
    } else if (status === 403 && error.response?.data?.code === 'PASSWORD_CHANGE_REQUIRED'
      && window.location.pathname !== '/change-password') {
      window.location.assign('/change-password');
    }
    return Promise.reject(error);
  },
);

export function errorMessage(error, fallback = t("Une erreur est survenue.")) {
  if (!error.response) return t("Serveur injoignable. Vérifiez votre connexion.");
  return error.response.data?.message || fallback;
}

export default client;
