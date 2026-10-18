import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { errorMessage } from '../api/client';
import AuthShell from '../components/AuthShell';
import PasswordInput from '../components/PasswordInput';
import Icon from '../components/Icon';

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('Veuillez remplir tous les champs.');
      return;
    }
    setSubmitting(true);
    try {
      const logged = await login(username.trim(), password);
      if (logged.mustChangePassword) {
        navigate('/change-password', { replace: true });
      } else {
        navigate(location.state?.from?.pathname || '/', { replace: true });
      }
    } catch (err) {
      setError(errorMessage(err, 'Connexion impossible.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell>
      <div>
        <h2 className="auth-heading">Connexion</h2>
        <p className="auth-sub">Entrez vos identifiants pour accéder au système</p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label htmlFor="username" className="auth-label">Identifiant</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Icon name="user" /></span>
            <input id="username" className={`auth-input${error ? ' has-error' : ''}`} value={username}
              onChange={(e) => { setUsername(e.target.value); setError(''); }}
              autoComplete="username" placeholder="Votre identifiant" autoFocus />
          </div>
        </div>

        <div className="auth-field">
          <label htmlFor="password" className="auth-label">Mot de passe</label>
          <PasswordInput id="password" value={password} hasError={!!error}
            onChange={(v) => { setPassword(v); setError(''); }} autoComplete="current-password" />
        </div>

        {error && (
          <div className="auth-error" role="alert"><Icon name="alert" size={14} /><span>{error}</span></div>
        )}

        <button type="submit" className="auth-submit" disabled={submitting}>
          {submitting ? <span className="btn-spinner" /> : <><Icon name="login" strokeWidth={2.5} /> Se connecter</>}
        </button>
        <p className="auth-hint">Mot de passe oublié ? Contactez votre administrateur.</p>
      </form>
    </AuthShell>
  );
}
