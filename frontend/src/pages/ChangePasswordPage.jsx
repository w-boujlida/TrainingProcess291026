import { t, useLocale, getLocale } from '../i18n';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { authApi } from '../api/auth.api';
import { errorMessage } from '../api/client';
import AuthShell from '../components/AuthShell';
import Layout from '../components/Layout';
import PageHeader from '../components/PageHeader';
import PasswordInput from '../components/PasswordInput';
import Icon from '../components/Icon';

const RULES = [
  { test: (p) => p.length >= 8, label: '8 caractères minimum' },
  { test: (p) => /[A-Z]/.test(p), label: 'Une majuscule' },
  { test: (p) => /[a-z]/.test(p), label: 'Une minuscule' },
  { test: (p) => /\d/.test(p), label: 'Un chiffre' },
  { test: (p) => /[^A-Za-z0-9]/.test(p), label: 'Un caractère spécial' },
];

function ChangePasswordForm({ forced }) {
  useLocale();
  const { setUser, logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const allValid = RULES.every((r) => r.test(next));
  const matches = next.length > 0 && next === confirm;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!allValid) return setError(t("Le nouveau mot de passe ne respecte pas les règles."));
    if (!matches) return setError(t("La confirmation ne correspond pas."));
    setSubmitting(true);
    try {
      const updated = await authApi.changePassword(current, next);
      setUser(updated);
      if (forced) {
        navigate('/', { replace: true });
      } else {
        setSuccess(t("Mot de passe modifié avec succès."));
        setCurrent(''); setNext(''); setConfirm('');
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleLogout = async () => {
    setError('');
    setSubmitting(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } catch (err) { setError(errorMessage(err, t("Déconnexion impossible. Réessayez."))); }
    finally { setSubmitting(false); }
  };

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="auth-field">
        <label htmlFor="current" className="auth-label">{t("Mot de passe actuel")}</label>
        <PasswordInput id="current" value={current} onChange={setCurrent} autoComplete="current-password" autoFocus />
      </div>

      <div className="auth-field">
        <label htmlFor="next" className="auth-label">{t("Nouveau mot de passe")}</label>
        <PasswordInput id="next" value={next} onChange={setNext} autoComplete="new-password" />
        <ul className="pwd-rules">
          {RULES.map((r) => (
            <li key={r.label} className={r.test(next) ? 'ok' : ''}>
              <span className="pwd-rule-dot">{r.test(next) && <Icon name="check" size={9} strokeWidth={3.5} />}</span>
              {t(r.label)}
            </li>
          ))}
        </ul>
      </div>

      <div className="auth-field">
        <label htmlFor="confirm" className="auth-label">{t("Confirmer le mot de passe")}</label>
        <PasswordInput id="confirm" value={confirm} onChange={setConfirm} autoComplete="new-password"
          hasError={confirm.length > 0 && !matches} />
        {confirm && !matches && <p className="field-error">{t("Les mots de passe ne correspondent pas.")}</p>}
      </div>

      {error && <div className="auth-error" role="alert"><Icon name="alert" size={14} /><span>{error}</span></div>}
      {success && <div className="auth-success" role="status"><Icon name="checkCircle" size={14} /><span>{success}</span></div>}

      <button type="submit" className="auth-submit" disabled={submitting || !current || !allValid || !matches}>
        {submitting ? <span className="btn-spinner" /> : <><Icon name="check" strokeWidth={2.5} /> {t("Enregistrer")}</>}
      </button>
      {forced && (
        <button type="button" className="auth-link" disabled={submitting} onClick={handleLogout}> {t("Se déconnecter")} </button>
      )}
    </form>
  );
}

export default function ChangePasswordPage() {
  useLocale();
  const { user } = useAuth();

  if (user.mustChangePassword) {
    return (
      <AuthShell>
        <div>
          <h2 className="auth-heading">{t("Nouveau mot de passe")}</h2>
          <p className="auth-sub"> {t("Bienvenue")} {user.fullName}{t(". Pour des raisons de sécurité, remplacez votre mot de passe temporaire.")} </p>
        </div>
        <ChangePasswordForm forced />
      </AuthShell>
    );
  }

  return (
    <Layout>
      <PageHeader title={t("Mot de passe")} subtitle={t("Modifier votre mot de passe de connexion")} />
      <div className="content">
        <div className="panel panel-narrow">
          <div className="panel-header">
            <div className="panel-icon"><Icon name="key" size={17} /></div>
            <div>
              <div className="panel-title">{t("Changer mon mot de passe")}</div>
              <div className="panel-sub">{t("Le nouveau mot de passe s'appliquera à votre prochaine connexion.")}</div>
            </div>
          </div>
          <div className="panel-body"><ChangePasswordForm forced={false} /></div>
        </div>
      </div>
    </Layout>
  );
}
