import { useMemo } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { USER_MANAGERS } from '../roles';
import Icon from './Icon';

function getCurrentShift() {
  const h = new Date().getHours();
  if (h >= 6 && h < 14) return { label: 'Shift Matin', time: '06:00 – 14:00' };
  if (h >= 14 && h < 22) return { label: 'Shift Après-midi', time: '14:00 – 22:00' };
  return { label: 'Shift Nuit', time: '22:00 – 06:00' };
}

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const shift = useMemo(getCurrentShift, []);
  const initials = user.fullName.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
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
              <span className={`sidebar-role-badge role-${user.role}`}>{user.roleLabel}</span>
            </div>
          </div>
        </div>

        <div className="sidebar-shift">
          <span className="sidebar-shift-dot" />
          <span className="sidebar-shift-label">{shift.label}</span>
          <span className="sidebar-shift-time">{shift.time}</span>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-group">
            <span className="nav-group-title">Principal</span>
            <NavLink to="/" end className={navClass}><Icon name="grid" />Vue d'ensemble</NavLink>
          </div>

          {USER_MANAGERS.includes(user.role) && (
            <div className="nav-group">
              <span className="nav-group-title">Gestion</span>
              <NavLink to="/users" className={navClass}><Icon name="users" />Utilisateurs</NavLink>
            </div>
          )}
        </nav>

        <div className="sidebar-footer">
          <NavLink to="/change-password" className={({ isActive }) => 'sidebar-footer-item' + (isActive ? ' active' : '')}>
            <Icon name="key" />Mot de passe
          </NavLink>
          <button className="sidebar-footer-item logout-item" onClick={handleLogout}>
            <Icon name="logout" />Déconnexion
          </button>
        </div>
      </aside>

      <main className="main-content">
        <div className="main-content-watermark">{children ?? <Outlet />}</div>
      </main>
    </div>
  );
}
