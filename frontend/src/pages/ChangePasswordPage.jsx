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
    if (!allValid) return setError('Le nouveau mot de passe ne respecte pas les règles.');
    if (!matches) return setError('La confirmation ne correspond pas.');
    setSubmitting(true);
    try {
      const updated = await authApi.changePassword(current, next);
      setUser(updated);
      if (forced) {
        navigate('/', { replace: true });
      } else {
        setSuccess('Mot de passe modifié avec succès.');
        setCurrent(''); setNext(''); setConfirm('');
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <div className="auth-field">
        <label htmlFor="current" className="auth-label">Mot de passe actuel</label>
        <PasswordInput id="current" value={current} onChange={setCurrent} autoComplete="current-password" autoFocus />
      </div>

      <div className="auth-field">
        <label htmlFor="next" className="auth-label">Nouveau mot de passe</label>
        <PasswordInput id="next" value={next} onChange={setNext} autoComplete="new-password" />
        <ul className="pwd-rules">
          {RULES.map((r) => (
            <li key={r.label} className={r.test(next) ? 'ok' : ''}>
              <span className="pwd-rule-dot">{r.test(next) && <Icon name="check" size={9} strokeWidth={3.5} />}</span>
              {r.label}
            </li>
          ))}
        </ul>
      </div>

      <div className="auth-field">
        <label htmlFor="confirm" className="auth-label">Confirmer le mot de passe</label>
        <PasswordInput id="confirm" value={confirm} onChange={setConfirm} autoComplete="new-password"
          hasError={confirm.length > 0 && !matches} />
        {confirm && !matches && <p className="field-error">Les mots de passe ne correspondent pas.</p>}
      </div>

      {error && <div className="auth-error" role="alert"><Icon name="alert" size={14} /><span>{error}</span></div>}
      {success && <div className="auth-success" role="status"><Icon name="checkCircle" size={14} /><span>{success}</span></div>}

      <button type="submit" className="auth-submit" disabled={submitting || !current || !allValid || !matches}>
        {submitting ? <span className="btn-spinner" /> : <><Icon name="check" strokeWidth={2.5} /> Enregistrer</>}
      </button>
      {forced && (
        <button type="button" className="auth-link" onClick={() => { logout(); navigate('/login', { replace: true }); }}>
          Se déconnecter
        </button>
      )}
    </form>
  );
}

export default function ChangePasswordPage() {
  const { user } = useAuth();

  if (user.mustChangePassword) {
    return (
      <AuthShell>
        <div>
          <h2 className="auth-heading">Nouveau mot de passe</h2>
          <p className="auth-sub">
            Bienvenue {user.fullName}. Pour des raisons de sécurité, remplacez votre mot de passe temporaire.
          </p>
        </div>
        <ChangePasswordForm forced />
      </AuthShell>
    );
  }

  return (
    <Layout>
      <PageHeader title="Mot de passe" subtitle="Modifier votre mot de passe de connexion" />
      <div className="content">
        <div className="panel panel-narrow">
          <div className="panel-header">
            <div className="panel-icon"><Icon name="key" size={17} /></div>
            <div>
              <div className="panel-title">Changer mon mot de passe</div>
              <div className="panel-sub">Le nouveau mot de passe s'appliquera à votre prochaine connexion.</div>
            </div>
          </div>
          <div className="panel-body"><ChangePasswordForm forced={false} /></div>
        </div>
      </div>
    </Layout>
  );
}
