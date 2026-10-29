import { t, useLocale, getLocale } from '../i18n';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { errorMessage } from '../api/client';
import AuthShell from '../components/AuthShell';
import PasswordInput from '../components/PasswordInput';
import Icon from '../components/Icon';

export default function LoginPage() {
  useLocale();
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [deviceName, setDeviceName] = useState(() => localStorage.getItem('tp_device_name') || '');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError(t("Veuillez remplir tous les champs."));
      return;
    }
    setSubmitting(true);
    try {
      const logged = await login(username.trim(), password, deviceName.trim() || null);
      localStorage.setItem('tp_device_name', deviceName.trim());
      if (logged.mustChangePassword) {
        navigate('/change-password', { replace: true });
      } else {
        navigate(location.state?.from?.pathname || '/', { replace: true });
      }
    } catch (err) {
      setError(errorMessage(err, t("Connexion impossible.")));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell>
      <div>
        <h2 className="auth-heading">{t("Connexion")}</h2>
        <p className="auth-sub">{t("Entrez vos identifiants pour accéder au système")}</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label htmlFor="username" className="auth-label">{t("Identifiant")}</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Icon name="user" /></span>
            <input id="username" className={`auth-input${error ? ' has-error' : ''}`} value={username}
              onChange={(e) => { setUsername(e.target.value); setError(''); }}
              autoComplete="username" placeholder={t("Votre identifiant")} autoFocus />
          </div>
        </div>

        <div className="auth-field">
          <label htmlFor="password" className="auth-label">{t("Mot de passe")}</label>
          <PasswordInput id="password" value={password} hasError={!!error}
            onChange={(v) => { setPassword(v); setError(''); }} autoComplete="current-password" />
        </div>

        {error && (
          <div className="auth-error" role="alert"><Icon name="alert" size={14} /><span>{error}</span></div>
        )}

        <div className="auth-field">
          <label htmlFor="device-name" className="auth-label">{t("Nom du poste (facultatif)")}</label>
          <input id="device-name" className="auth-input" value={deviceName} maxLength={100}
            onChange={(event) => setDeviceName(event.target.value)} placeholder={t("ex : PC-Formation-01")} autoComplete="off" />
        </div>
        <p className="auth-hint">{t("Les connexions, consultations et actions sont journalisées pendant 90 jours avec l’adresse IP, le navigateur et le nom de poste déclaré. Journal accessible uniquement au Super Admin.")}</p>

        <button type="submit" className="auth-submit" disabled={submitting}>
          {submitting ? <span className="btn-spinner" /> : <><Icon name="login" strokeWidth={2.5} /> {t("Se connecter")}</>}
        </button>
        <p className="auth-hint">{t("Mot de passe oublié ? Contactez votre administrateur.")}</p>
      </form>
    </AuthShell>
  );
}
