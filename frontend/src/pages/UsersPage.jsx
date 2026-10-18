import { useEffect, useMemo, useState } from 'react';
import { usersApi } from '../api/users.api';
import { errorMessage } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { ROLE_LABELS, canManage } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

const EMPTY_FORM = { username: '', fullName: '', email: '', role: '', password: '' };

function formatDate(value) {
  return value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

function initials(name) {
  return name.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

function UserModal({ user, roles, onClose, onSaved }) {
  const isEdit = Boolean(user);
  const [form, setForm] = useState(
    isEdit ? { ...EMPTY_FORM, ...user, email: user.email || '' } : { ...EMPTY_FORM, role: roles[0]?.code || '' },
  );
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      const saved = isEdit
        ? await usersApi.update(user.id, { fullName: form.fullName, email: form.email, role: form.role })
        : await usersApi.create(form);
      onSaved(saved, isEdit);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className="modal-card" onSubmit={handleSubmit} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div className="modal-head-title">
            <Icon name={isEdit ? 'edit' : 'plus'} size={17} />
            {isEdit ? "Modifier l'utilisateur" : 'Nouvel utilisateur'}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Fermer"><Icon name="x" size={18} /></button>
        </div>

        <div className="modal-body-pad">
          {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}

          <div className="form-grid-2">
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-username">Identifiant</label>
              <input id="u-username" className="form-input mono" value={form.username} onChange={set('username')}
                disabled={isEdit} required autoFocus={!isEdit} placeholder="ex : j.dupont" />
            </div>
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-role">Rôle</label>
              <select id="u-role" className="form-input" value={form.role} onChange={set('role')} required>
                {isEdit && !roles.some((r) => r.code === form.role) && (
                  <option value={form.role}>{ROLE_LABELS[form.role]}</option>
                )}
                {roles.map((r) => <option key={r.code} value={r.code}>{r.label}</option>)}
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label label-required" htmlFor="u-fullname">Nom complet</label>
            <input id="u-fullname" className="form-input" value={form.fullName} onChange={set('fullName')}
              required autoFocus={isEdit} placeholder="Prénom Nom" />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="u-email">E-mail <span className="label-optional">(optionnel)</span></label>
            <input id="u-email" type="email" className="form-input" value={form.email} onChange={set('email')} placeholder="prenom.nom@sage.com" />
          </div>

          {!isEdit && (
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-password">Mot de passe temporaire</label>
              <input id="u-password" className="form-input mono" value={form.password} onChange={set('password')} autoComplete="new-password" required />
              <p className="help-text">8 caractères min., majuscule, minuscule, chiffre et caractère spécial. L'utilisateur devra le changer à sa première connexion.</p>
            </div>
          )}
        </div>

        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Annuler</button>
          <button className="btn btn-primary" disabled={saving}>
            {saving ? <span className="btn-spinner" /> : <Icon name="check" strokeWidth={2.5} />}
            Enregistrer
          </button>
        </div>
      </form>
    </div>
  );
}

export default function UsersPage() {
  const { user: me } = useAuth();
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [modal, setModal] = useState(null);

  useEffect(() => {
    Promise.all([usersApi.list(), usersApi.roles()])
      .then(([u, r]) => { setUsers(u); setRoles(r); })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return users.filter((u) =>
      (!roleFilter || u.role === roleFilter)
      && (!q || u.fullName.toLowerCase().includes(q) || u.username.toLowerCase().includes(q)
        || (u.email || '').toLowerCase().includes(q)));
  }, [users, search, roleFilter]);

  const stats = useMemo(() => ({
    total: users.length,
    active: users.filter((u) => u.active).length,
    inactive: users.filter((u) => !u.active).length,
    pending: users.filter((u) => u.mustChangePassword).length,
  }), [users]);

  const replace = (updated) => setUsers((list) => list.map((u) => (u.id === updated.id ? updated : u)));

  const run = async (fn) => {
    setError(''); setNotice('');
    try { await fn(); } catch (err) { setError(errorMessage(err)); }
  };

  const toggleStatus = (u) => run(async () => {
    replace(await usersApi.setStatus(u.id, !u.active));
    setNotice(`Compte ${u.active ? 'désactivé' : 'activé'} : ${u.fullName}`);
  });

  const resetPassword = (u) => {
    if (!window.confirm(`Réinitialiser le mot de passe de ${u.fullName} ?`)) return;
    run(async () => {
      const { temporaryPassword } = await usersApi.resetPassword(u.id);
      replace({ ...u, mustChangePassword: true });
      setNotice(`Mot de passe temporaire de ${u.fullName} : ${temporaryPassword} — à communiquer à l'utilisateur, il ne sera plus affiché.`);
    });
  };

  const remove = (u) => {
    if (!window.confirm(`Supprimer définitivement le compte de ${u.fullName} ?`)) return;
    run(async () => {
      await usersApi.remove(u.id);
      setUsers((list) => list.filter((x) => x.id !== u.id));
      setNotice(`Compte supprimé : ${u.fullName}`);
    });
  };

  const handleSaved = (saved, isEdit) => {
    if (isEdit) replace(saved);
    else setUsers((list) => [...list, saved].sort((a, b) => a.fullName.localeCompare(b.fullName)));
    setNotice(isEdit ? `Utilisateur modifié : ${saved.fullName}` : `Utilisateur créé : ${saved.fullName}`);
    setModal(null);
  };

  return (
    <>
      <PageHeader title="Utilisateurs" subtitle="Gestion des comptes et des rôles" />
      <div className="content">
        <div className="stats-grid">
          <div className="stat-card stat-green">
            <div className="stat-header"><span className="stat-title">Total comptes</span><span className="stat-icon"><Icon name="users" size={18} /></span></div>
            <div className="stat-value">{stats.total}</div>
          </div>
          <div className="stat-card stat-teal">
            <div className="stat-header"><span className="stat-title">Actifs</span><span className="stat-icon"><Icon name="userCheck" size={18} /></span></div>
            <div className="stat-value">{stats.active}</div>
          </div>
          <div className="stat-card stat-red">
            <div className="stat-header"><span className="stat-title">Inactifs</span><span className="stat-icon"><Icon name="power" size={18} /></span></div>
            <div className="stat-value">{stats.inactive}</div>
          </div>
          <div className="stat-card stat-amber">
            <div className="stat-header"><span className="stat-title">MDP à changer</span><span className="stat-icon"><Icon name="key" size={18} /></span></div>
            <div className="stat-value">{stats.pending}</div>
          </div>
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {notice && <div className="alert alert-success"><Icon name="checkCircle" size={14} />{notice}</div>}

        <div className="table-container">
          <div className="table-header">
            <div className="table-title">Liste des utilisateurs <span className="table-count">{filtered.length}</span></div>
            <div className="table-controls">
              <div className="search-container">
                <span className="search-icon"><Icon name="search" size={14} /></span>
                <input className="search-input" placeholder="Rechercher…" value={search} onChange={(e) => setSearch(e.target.value)} />
              </div>
              <select className="filter-select" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
                <option value="">Tous les rôles</option>
                {Object.entries(ROLE_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
              </select>
              <button className="add-btn" onClick={() => setModal({})} disabled={!roles.length}>
                <Icon name="plus" size={13} strokeWidth={2.5} />Nouvel utilisateur
              </button>
            </div>
          </div>

          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="left">Utilisateur</th><th>Identifiant</th><th>Rôle</th><th>Statut</th><th>Dernière connexion</th><th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan="6" className="table-empty"><span className="spinner" /></td></tr>}
                {!loading && filtered.length === 0 && <tr><td colSpan="6" className="table-empty">Aucun utilisateur trouvé.</td></tr>}
                {filtered.map((u) => {
                  const self = u.id === me.id;
                  const manageable = !self && canManage(me.role, u.role);
                  return (
                    <tr key={u.id}>
                      <td className="left">
                        <div className="user-cell">
                          <div className={`user-avatar role-bg-${u.role}`}>{initials(u.fullName)}</div>
                          <div>
                            <div className="user-cell-name">{u.fullName}{self && <span className="self-tag">vous</span>}</div>
                            <div className="user-cell-mail">{u.email || '—'}</div>
                          </div>
                        </div>
                      </td>
                      <td className="mono">{u.username}</td>
                      <td><span className={`role-badge role-${u.role}`}>{u.roleLabel}</span></td>
                      <td>
                        <span className={`status-badge ${u.active ? 'status-active' : 'status-inactive'}`}>{u.active ? 'Actif' : 'Inactif'}</span>
                        {u.mustChangePassword && <div className="status-note">MDP à changer</div>}
                      </td>
                      <td className="muted-cell">{formatDate(u.lastLoginAt)}</td>
                      <td>
                        {manageable ? (
                          <div className="row-actions">
                            <button className="action-btn edit-btn" title="Modifier" onClick={() => setModal(u)}><Icon name="edit" size={13} /></button>
                            <button className="action-btn key-btn" title="Réinitialiser le mot de passe" onClick={() => resetPassword(u)}><Icon name="refresh" size={13} /></button>
                            <button className={`action-btn ${u.active ? 'warn-btn' : 'save-btn'}`} title={u.active ? 'Désactiver' : 'Activer'} onClick={() => toggleStatus(u)}><Icon name="power" size={13} /></button>
                            <button className="action-btn delete-btn" title="Supprimer" onClick={() => remove(u)}><Icon name="trash" size={13} /></button>
                          </div>
                        ) : <span className="muted-cell">—</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {modal && (
        <UserModal user={modal.id ? modal : null} roles={roles} onClose={() => setModal(null)} onSaved={handleSaved} />
      )}
    </>
  );
}
