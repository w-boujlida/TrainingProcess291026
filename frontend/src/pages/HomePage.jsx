import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { USER_MANAGERS } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

const ROLE_INTRO = {
  SUPER_ADMIN: 'Vous disposez de tous les droits, y compris la gestion des administrateurs.',
  ADMIN: 'Vous gérez les comptes Superviseur, Formatrice et Team Leader.',
  SUPERVISEUR: 'Vous supervisez les sessions de formation et leurs résultats.',
  FORMATRICE: 'Vous animez et évaluez les sessions de formation.',
  TEAM_LEADER: 'Vous suivez la formation des membres de votre équipe.',
};

function formatDate(v) {
  return v ? new Date(v).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

export default function HomePage() {
  const { user } = useAuth();
  const isManager = USER_MANAGERS.includes(user.role);

  return (
    <>
      <PageHeader title="Vue d'ensemble" subtitle="Training Process — SAGE Automotive Interiors" />
      <div className="content">
        <div className="welcome-banner">
          <div>
            <div className="welcome-kicker">Bienvenue</div>
            <h1 className="welcome-title">{user.fullName}</h1>
            <p className="welcome-text">{ROLE_INTRO[user.role]}</p>
          </div>
          <img src="/sage-logo.png" alt="" aria-hidden="true" className="welcome-logo" />
        </div>

        <div className="stats-grid">
          <div className="stat-card stat-green">
            <div className="stat-header">
              <span className="stat-title">Rôle</span>
              <span className="stat-icon"><Icon name="shield" size={18} /></span>
            </div>
            <div className="stat-text">{user.roleLabel}</div>
          </div>
          <div className="stat-card stat-teal">
            <div className="stat-header">
              <span className="stat-title">Identifiant</span>
              <span className="stat-icon"><Icon name="user" size={18} /></span>
            </div>
            <div className="stat-text mono">{user.username}</div>
          </div>
          <div className="stat-card stat-amber">
            <div className="stat-header">
              <span className="stat-title">Dernière connexion</span>
              <span className="stat-icon"><Icon name="clock" size={18} /></span>
            </div>
            <div className="stat-text">{formatDate(user.lastLoginAt)}</div>
          </div>
          {isManager ? (
            <Link to="/users" className="stat-card stat-blue stat-link">
              <div className="stat-header">
                <span className="stat-title">Administration</span>
                <span className="stat-icon"><Icon name="users" size={18} /></span>
              </div>
              <div className="stat-text">Gérer les utilisateurs →</div>
            </Link>
          ) : (
            <div className="stat-card stat-blue">
              <div className="stat-header">
                <span className="stat-title">Formations</span>
                <span className="stat-icon"><Icon name="graduation" size={18} /></span>
              </div>
              <div className="stat-text muted">Bientôt disponible</div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
