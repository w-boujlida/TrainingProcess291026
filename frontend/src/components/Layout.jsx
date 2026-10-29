import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { RECRUITMENT_CATEGORIES, USER_MANAGERS } from '../roles';
import Icon from './Icon';
import { errorMessage } from '../api/client';
import LanguageSelector from './LanguageSelector';

function getCurrentShift() {
  const h = new Date().getHours();
  if (h >= 6 && h < 14) return { label: 'Shift Matin', time: '06:00 – 14:00' };
  if (h >= 14 && h < 22) return { label: 'Shift Après-midi', time: '14:00 – 22:00' };
  return { label: 'Shift Nuit', time: '22:00 – 06:00' };
}

export default function Layout({ children }) {
  useLocale();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const shift = useMemo(getCurrentShift, []);
  const { pathname } = useLocation();
  const employeesActive = pathname.startsWith('/recruitment');
  const [employeesOpen, setEmployeesOpen] = useState(true);
  const [logoutError, setLogoutError] = useState('');
  const [auditFailure, setAuditFailure] = useState(() => sessionStorage.getItem('tp_audit_failure') === 'true');
  useEffect(() => {
    const notify = () => setAuditFailure(true);
    window.addEventListener('audit-write-failed', notify);
    return () => window.removeEventListener('audit-write-failed', notify);
  }, []);
  useEffect(() => { if (employeesActive) setEmployeesOpen(true); }, [employeesActive]);
  const initials = user.fullName.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

  const handleLogout = async () => {
    setLogoutError('');
    try {
      await logout();
      navigate('/login', { replace: true });
    } catch (err) { setLogoutError(errorMessage(err, t("Déconnexion impossible. Réessayez."))); }
  };

  const navClass = ({ isActive }) => 'nav-item' + (isActive ? ' active' : '');

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-header">
          <img className="sidebar-logo-image" src="/sage-logo.png" alt="SAGE Automotive Interiors" />
          <div className="sidebar-app-name">Training Process</div>
        </div>

        <div className="sidebar-user-card">
          <div className="sidebar-user-avatar">{initials}</div>
          <div className="sidebar-user-info">
            <div className="sidebar-user-name">{user.fullName}</div>
            <div className="sidebar-user-meta">
              <span className="sidebar-user-matricule">{user.username}</span>
              <span className={`sidebar-role-badge role-${user.role}`}>{t(user.roleLabel)}</span>
            </div>
          </div>
        </div>

        <div className="sidebar-shift">
          <span className="sidebar-shift-dot" />
          <span className="sidebar-shift-label">{t(shift.label)}</span>
          <span className="sidebar-shift-time">{shift.time}</span>
        </div>

        <nav className="sidebar-nav">
          {!user.role.startsWith('JOB_') && <div className="nav-group">
            <span className="nav-group-title">{t("Principal")}</span>
            <NavLink to="/" end className={navClass}><Icon name="grid" />{t("Vue d'ensemble")}</NavLink>
          </div>}

          {!user.role.startsWith('JOB_') && <div className="nav-group">
            <span className="nav-group-title">{t("Recrutement")}</span>
            <button type="button" className={`nav-item nav-parent${employeesActive ? ' parent-active' : ''}`}
              onClick={() => setEmployeesOpen((o) => !o)} aria-expanded={employeesOpen}>
              <Icon name="users" />{t("Employés")} <Icon name="chevron" size={13} className={`nav-chevron${employeesOpen ? ' open' : ''}`} />
            </button>
            {employeesOpen && (
              <div className="nav-sub">
                {RECRUITMENT_CATEGORIES.map((c) => (
                  <NavLink key={c.code} to={`/recruitment/${c.slug}`} className={navClass}><Icon name={c.icon} />{t(c.label)}</NavLink>
                ))}
              </div>
            )}
          </div>}

          {USER_MANAGERS.includes(user.role) && (
            <div className="nav-group">
              <span className="nav-group-title">{t("Gestion")}</span>
              <NavLink to="/users" className={navClass}><Icon name="users" />{t("Utilisateurs")}</NavLink>
              {user.role === 'SUPER_ADMIN' && <NavLink to="/audit" className={navClass}><Icon name="shield" />{t("Journal d’audit")}</NavLink>}
            </div>
          )}
        </nav>

        <div className="sidebar-footer">
          <LanguageSelector />
          <NavLink to="/change-password" className={({ isActive }) => 'sidebar-footer-item' + (isActive ? ' active' : '')}>
            <Icon name="key" />{t("Mot de passe")} </NavLink>
          <button className="sidebar-footer-item logout-item" onClick={handleLogout}>
            <Icon name="logout" />{t("Déconnexion")} </button>
        </div>
      </aside>

      <main className="main-content">
        {logoutError && <div className="alert alert-error" role="alert">{logoutError}</div>}
        {auditFailure && <div className="alert alert-error" role="alert"> {t("Une opération n’a pas pu être inscrite au journal d’audit. Son résultat reste celui affiché ; ne la répétez pas sans vérifier. Contactez l’administrateur pour contrôler la journalisation.")} </div>}
        <div className="main-content-watermark">{children ?? <Outlet />}</div>
      </main>
    </div>
  );
}
