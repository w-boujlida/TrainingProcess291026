import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useMemo, useRef, useState } from 'react';
import { usersApi } from '../api/users.api';
import { errorMessage } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { ROLE_LABELS, canManage } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';
import ImportExcelModal from '../components/ImportExcelModal';
import UserHrProfileModal from '../components/UserHrProfileModal';
import HrUpdateImportModal from '../components/HrUpdateImportModal';
import { DEFAULT_FILTERS, filterUsers, isFiltered, quickCounts } from '../usersFilters';

const PAGE_SIZE = 200;
const STATUS_CHIPS = [['ALL', 'Tous'], ['ACTIVE', 'Actifs'], ['INACTIVE', 'Inactifs'], ['PENDING', 'MDP à changer']];
const TYPE_CHIPS = [['ALL', 'Tous'], ['CADRE', 'Cadre'], ['DIRECT', 'Direct'], ['INDIRECT', 'Indirect'], ['NONE', 'Sans type']];
const SORT_OPTIONS = [['NAME_ASC', 'Nom A → Z'], ['NAME_DESC', 'Nom Z → A'], ['USERNAME', 'Matricule / identifiant'],
  ['LAST_LOGIN', 'Dernière connexion'], ['RECENT', 'Comptes les plus récents']];

const EMPTY_FORM = { username: '', fullName: '', email: '', role: '', password: '', workType: '' };
const WORK_TYPES = { CADRE: 'Cadre', DIRECT: 'Direct', INDIRECT: 'Indirect' };

function formatDate(value) {
  return value ? new Date(value).toLocaleString(getLocale(), { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

function initials(name) {
  return name.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

function UserModal({ user, roles, onClose, onSaved }) {
  useLocale();
  const isEdit = Boolean(user);
  const [form, setForm] = useState(
    isEdit ? { ...EMPTY_FORM, ...user, email: user.email || '', workType: user.workType || '' } : { ...EMPTY_FORM, role: roles[0]?.code || '' },
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
        ? await usersApi.update(user.id, { fullName: form.fullName, email: form.email, role: form.role, workType: form.workType })
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
            {isEdit ? t("Modifier l'utilisateur") : t("Nouvel utilisateur")}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label={t("Fermer")}><Icon name="x" size={18} /></button>
        </div>

        <div className="modal-body-pad">
          {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}

          <div className="form-grid-2">
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-username">{t("Identifiant")}</label>
              <input id="u-username" className="form-input mono" value={form.username} onChange={set('username')}
                disabled={isEdit} required autoFocus={!isEdit} placeholder={t("ex : j.dupont")} />
            </div>
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-role">{t("Rôle")}</label>
              <select id="u-role" className="form-input" value={form.role} onChange={set('role')} required>
                {isEdit && !roles.some((r) => r.code === form.role) && (
                  <option value={form.role}>{t(user.roleLabel || ROLE_LABELS[form.role] || form.role)}</option>
                )}
                {roles.map((r) => <option key={r.code} value={r.code}>{t(r.label)}</option>)}
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="u-work-type">{t("Type de travail")} <span className="label-optional">{t("(optionnel)")}</span></label>
            <select id="u-work-type" className="form-input" value={form.workType} onChange={set('workType')}>
              <option value="">—</option>
              {Object.entries(WORK_TYPES).map(([code, label]) => <option key={code} value={code}>{t(label)}</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label label-required" htmlFor="u-fullname">{t("Nom complet")}</label>
            <input id="u-fullname" className="form-input" value={form.fullName} onChange={set('fullName')}
              required autoFocus={isEdit} placeholder={t("Prénom Nom")} />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="u-email">{t("E-mail")} <span className="label-optional">{t("(optionnel)")}</span></label>
            <input id="u-email" type="email" className="form-input" value={form.email} onChange={set('email')} placeholder="prenom.nom@sage.com" />
          </div>

          {!isEdit && (
            <div className="form-group">
              <label className="form-label label-required" htmlFor="u-password">{t("Mot de passe temporaire")}</label>
              <input id="u-password" className="form-input mono" value={form.password} onChange={set('password')} autoComplete="new-password" required />
              <p className="help-text">{t("8 caractères min., majuscule, minuscule, chiffre et caractère spécial. L'utilisateur devra le changer à sa première connexion.")}</p>
            </div>
          )}
        </div>

        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>{t("Annuler")}</button>
          <button className="btn btn-primary" disabled={saving}>
            {saving ? <span className="btn-spinner" /> : <Icon name="check" strokeWidth={2.5} />} {t("Enregistrer")} </button>
        </div>
      </form>
    </div>
  );
}

export default function UsersPage() {
  useLocale();
  const { user: me } = useAuth();
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [visible, setVisible] = useState(PAGE_SIZE);
  const [modal, setModal] = useState(null);
  const [hrUser, setHrUser] = useState(null);
  const [importOpen, setImportOpen] = useState(false);
  const [hrImportOpen, setHrImportOpen] = useState(false);
  const [newRecruits, setNewRecruits] = useState([]);
  const [downloading, setDownloading] = useState(false);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [selectionWorkType, setSelectionWorkType] = useState('');
  const [applyingSelection, setApplyingSelection] = useState(false);
  const selectAllRef = useRef(null);

  useEffect(() => {
    Promise.all([usersApi.list(), usersApi.roles()])
      .then(([u, r]) => { setUsers(u); setRoles(r); })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => filterUsers(users, filters), [users, filters]);
  const counts = useMemo(() => quickCounts(users, filters), [users, filters]);
  const shown = filtered.slice(0, visible);
  const setFilter = (key, value) => {
    setFilters((previous) => ({ ...previous, [key]: value }));
    setVisible(PAGE_SIZE);
    if (key !== 'sort') setSelectedIds(new Set());
  };
  const resetFilters = () => { setFilters(DEFAULT_FILTERS); setVisible(PAGE_SIZE); setSelectedIds(new Set()); };

  const selected = filtered.filter((u) => selectedIds.has(u.id));
  const allSelected = filtered.length > 0 && selected.length === filtered.length;
  const canEditSelection = selected.length > 0 && selected.every((u) => u.id !== me.id && canManage(me.role, u.role));
  useEffect(() => {
    if (selectAllRef.current) selectAllRef.current.indeterminate = selected.length > 0 && !allSelected;
  }, [selected.length, allSelected]);

  const toggleSelection = (id) => {
    if (applyingSelection) return;
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const applySelection = async (action) => {
    if (!canEditSelection || applyingSelection || (action === 'WORK_TYPE' && !selectionWorkType)) return;
    const label = action === 'WORK_TYPE' ? t(WORK_TYPES[selectionWorkType])
      : action === 'ACTIVATE' ? t("Activer") : t("Désactiver");
    if (!window.confirm(t("Appliquer {0} à {1} utilisateur(s) sélectionné(s) ?", { 0: label, 1: selected.length }))) return;
    setApplyingSelection(true);
    setError(''); setNotice('');
    try {
      const updated = await usersApi.applySelection({
        ids: selected.map((u) => u.id), action,
        workType: action === 'WORK_TYPE' ? selectionWorkType : null,
      });
      const byId = new Map(updated.map((u) => [u.id, u]));
      setUsers((previous) => previous.map((u) => byId.get(u.id) || u));
      setNotice(t("{0} utilisateur(s) modifié(s).", { 0: updated.length }));
      setSelectedIds(new Set());
    } catch (err) {
      setError(errorMessage(err));
    } finally { setApplyingSelection(false); }
  };

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
    setNotice(t("Compte {0} : {1}", { 0: u.active ? 'désactivé' : 'activé', 1: u.fullName }));
  });

  const resetPassword = (u) => {
    if (!window.confirm(t("Réinitialiser le mot de passe de {0} ?", { 0: u.fullName }))) return;
    run(async () => {
      const { temporaryPassword } = await usersApi.resetPassword(u.id);
      replace({ ...u, mustChangePassword: true });
      setNotice(t("Mot de passe temporaire de {0} : {1} — à communiquer à l'utilisateur, il ne sera plus affiché.", { 0: u.fullName, 1: temporaryPassword }));
    });
  };

  const remove = (u) => {
    if (!window.confirm(t("Supprimer définitivement le compte de {0} ?", { 0: u.fullName }))) return;
    run(async () => {
      await usersApi.remove(u.id);
      setUsers((list) => list.filter((x) => x.id !== u.id));
      setNotice(t("Compte supprimé : {0}", { 0: u.fullName }));
    });
  };

  const handleSaved = (saved, isEdit) => {
    if (isEdit) replace(saved);
    else setUsers((list) => [...list, saved].sort((a, b) => a.fullName.localeCompare(b.fullName)));
    setNotice(isEdit ? t("Utilisateur modifié : {0}", { 0: saved.fullName }) : t("Utilisateur créé : {0}", { 0: saved.fullName }));
    setModal(null);
  };

  const download = async (action) => {
    setDownloading(true);
    setError('');
    try { await action(); } catch (err) { setError(errorMessage(err)); }
    finally { setDownloading(false); }
  };

  const handleImported = async (result) => {
    setUsers(await usersApi.list());
    setNotice(t("{0} compte(s) créé(s), {1} ligne(s) en erreur. Aucun compte existant modifié.", { 0: result.created, 1: result.skipped }));
  };

  return (
    <>
      <PageHeader title={t("Utilisateurs")} subtitle={t("Gestion des comptes et des rôles")} />
      <div className="content">
        <div className="stats-grid">
          {[['ALL', 'stat-green', 'Total comptes', 'users', stats.total], ['ACTIVE', 'stat-teal', 'Actifs', 'userCheck', stats.active],
            ['INACTIVE', 'stat-red', 'Inactifs', 'power', stats.inactive], ['PENDING', 'stat-amber', 'MDP à changer', 'key', stats.pending]]
            .map(([status, color, title, icon, value]) => (
              <button type="button" key={status} disabled={applyingSelection} aria-pressed={filters.status === status}
                title={t("Filtrer la liste")} onClick={() => setFilter('status', status)}
                className={`stat-card stat-card-filter ${color}${filters.status === status ? ' stat-card-selected' : ''}`}>
                <div className="stat-header"><span className="stat-title">{t(title)}</span><span className="stat-icon"><Icon name={icon} size={18} /></span></div>
                <div className="stat-value">{value}</div>
              </button>
            ))}
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {notice && <div className="alert alert-success"><Icon name="checkCircle" size={14} />{notice}</div>}
        {newRecruits.length > 0 && <div className="alert alert-success" role="status">
          {t('Nouvelles recrues à traiter dans Recrutement')} : {newRecruits.length}.
          <details><summary>{t('Afficher la liste')}</summary><ul>
            {newRecruits.map((u) => <li key={u.username}>{u.username} — {u.fullName}</li>)}
          </ul></details>
        </div>}

        <div className="table-container">
          <div className="table-header">
            <div className="table-title">{t("Liste des utilisateurs")} <span className="table-count">{filtered.length}</span></div>
            <div className="table-controls">
              <div className="search-container">
                <span className="search-icon"><Icon name="search" size={14} /></span>
                <input className="search-input" placeholder={t("Nom, matricule, e-mail…")} value={filters.search} disabled={applyingSelection}
                  aria-label={t("Rechercher")} onChange={(e) => setFilter('search', e.target.value)} />
              </div>
              <select className="filter-select" value={filters.role} disabled={applyingSelection} aria-label={t("Rôle")}
                onChange={(e) => setFilter('role', e.target.value)}>
                <option value="">{t("Tous les rôles")}</option>
                {[...new Map([...roles, ...users.map((u) => ({ code: u.role, label: u.roleLabel }))]
                  .map((r) => [r.code, r])).values()].map((r) =>
                  <option key={r.code} value={r.code}>{t(r.label)}</option>)}
              </select>
              <select className="filter-select" value={filters.sort} disabled={applyingSelection} aria-label={t("Trier par")}
                onChange={(e) => setFilter('sort', e.target.value)}>
                {SORT_OPTIONS.map(([code, label]) => <option key={code} value={code}>{t("Trier : {0}", { 0: t(label) })}</option>)}
              </select>
              <button type="button" className="tool-btn" disabled={applyingSelection || downloading || loading}
                title={t("Exporter tous les comptes, sans mot de passe")} onClick={() => download(usersApi.exportExcel)}>
                <Icon name="download" size={14} />{t("Exporter")} </button>
              <button type="button" className="tool-btn" disabled={applyingSelection || downloading || !roles.length}
                onClick={() => download(usersApi.template)}>
                <Icon name="fileSheet" size={14} />{t("Modèle .xlsx")} </button>
              {me.role === 'SUPER_ADMIN' && <button type="button" className="tool-btn" disabled={applyingSelection || loading}
                onClick={() => setHrImportOpen(true)}><Icon name="upload" size={14} />{t('Importer les mises à jour RH')}</button>}
              <button type="button" className="tool-btn" disabled={applyingSelection || !roles.length} onClick={() => setImportOpen(true)}>
                <Icon name="upload" size={14} />{t("Importer")} </button>
              <button className="add-btn" onClick={() => setModal({})} disabled={applyingSelection || !roles.length}>
                <Icon name="plus" size={13} strokeWidth={2.5} />{t("Nouvel utilisateur")} </button>
            </div>
          </div>

          <div className="user-quick-filters">
            <div className="chip-group" role="group" aria-label={t("Statut")}>
              <span className="chip-label">{t("Statut")}</span>
              {STATUS_CHIPS.map(([code, label]) => (
                <button type="button" key={code} disabled={applyingSelection} aria-pressed={filters.status === code}
                  className={`filter-chip${filters.status === code ? ' filter-chip-on' : ''}`} onClick={() => setFilter('status', code)}>
                  {t(label)} <span className="chip-count">{counts.status[code]}</span>
                </button>))}
            </div>
            <div className="chip-group" role="group" aria-label={t("Type de travail")}>
              <span className="chip-label">{t("Type de travail")}</span>
              {TYPE_CHIPS.map(([code, label]) => (
                <button type="button" key={code} disabled={applyingSelection} aria-pressed={filters.type === code}
                  className={`filter-chip${filters.type === code ? ' filter-chip-on' : ''}`} onClick={() => setFilter('type', code)}>
                  {t(label)} <span className="chip-count">{counts.type[code]}</span>
                </button>))}
            </div>
            {isFiltered(filters) && <button type="button" className="tool-btn" disabled={applyingSelection} onClick={resetFilters}>
              <Icon name="refresh" size={13} />{t("Réinitialiser les filtres")}</button>}
          </div>

          <div className="user-selection-toolbar" aria-busy={applyingSelection}>
            <span role="status">{t("{0} sélectionné(s)", { 0: selected.length })}</span>
            <button type="button" className="tool-btn" disabled={applyingSelection || !filtered.length}
              onClick={() => setSelectedIds(allSelected ? new Set() : new Set(filtered.map((u) => u.id)))}>
              {allSelected ? t("Tout désélectionner") : t("Tout sélectionner")}
            </button>
            <select className="filter-select" aria-label={t("Type de travail de la sélection")}
              value={selectionWorkType} disabled={applyingSelection || !canEditSelection}
              onChange={(e) => setSelectionWorkType(e.target.value)}>
              <option value="">{t("Type de travail")}</option>
              {Object.entries(WORK_TYPES).map(([code, label]) => <option key={code} value={code}>{t(label)}</option>)}
            </select>
            <button type="button" className="tool-btn" disabled={applyingSelection || !canEditSelection || !selectionWorkType}
              onClick={() => applySelection('WORK_TYPE')}>{t("Appliquer le type")}</button>
            <button type="button" className="tool-btn" disabled={applyingSelection || !canEditSelection}
              onClick={() => applySelection('ACTIVATE')}>{t("Activer")}</button>
            <button type="button" className="tool-btn" disabled={applyingSelection || !canEditSelection}
              onClick={() => applySelection('DEACTIVATE')}>{t("Désactiver")}</button>
            <button type="button" className="tool-btn" disabled={applyingSelection || downloading || !selected.length}
              onClick={() => download(() => usersApi.exportSelection(selected.map((u) => u.id)))}>
              {t("Exporter la sélection")}
            </button>
            {selected.length > 0 && !canEditSelection &&
              <span>{t("Retirez votre compte et les comptes non modifiables pour appliquer une action.")}</span>}
          </div>

          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th><input type="checkbox" ref={selectAllRef} checked={allSelected}
                    disabled={applyingSelection || !filtered.length} aria-label={t("Tout sélectionner")}
                    onChange={() => setSelectedIds(allSelected ? new Set() : new Set(filtered.map((u) => u.id)))} /></th>
                  <th className="left">{t("Utilisateur")}</th><th>{t("Identifiant")}</th><th>{t("Rôle")}</th><th>{t("Type de travail")}</th><th>{t("Statut")}</th><th>{t("Dernière connexion")}</th><th>{t("Actions")}</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan="8" className="table-empty"><span className="spinner" /></td></tr>}
                {!loading && filtered.length === 0 && <tr><td colSpan="8" className="table-empty">{t("Aucun utilisateur trouvé.")}</td></tr>}
                {shown.map((u) => {
                  const self = u.id === me.id;
                  const manageable = !self && canManage(me.role, u.role);
                  return (
                    <tr key={u.id} aria-selected={selectedIds.has(u.id)}
                      className={`user-selectable-row${selectedIds.has(u.id) ? ' user-selected-row' : ''}`}
                      onClick={(event) => {
                        if (!event.target.closest('button, input, select, a, label')) toggleSelection(u.id);
                      }}>
                      <td><input type="checkbox" checked={selectedIds.has(u.id)} disabled={applyingSelection}
                        aria-label={t("Sélectionner {0}", { 0: u.username })} onChange={() => toggleSelection(u.id)} /></td>
                      <td className="left">
                        <div className="user-cell">
                          <div className={`user-avatar role-bg-${u.role}`}>{initials(u.fullName)}</div>
                          <div>
                            <div className="user-cell-name">{u.fullName}{self && <span className="self-tag">{t("vous")}</span>}</div>
                            <div className="user-cell-mail">{u.email || '—'}</div>
                          </div>
                        </div>
                      </td>
                      <td className="mono">{u.username}</td>
                      <td><span className={`role-badge role-${u.role}`}>{t(u.roleLabel)}</span></td>
                      <td>{u.workType ? t(WORK_TYPES[u.workType]) : '—'}</td>
                      <td>
                        <span className={`status-badge ${u.active ? 'status-active' : 'status-inactive'}`}>{u.active ? t("Actif") : t("Inactif")}</span>
                        {u.mustChangePassword && <div className="status-note">{t("MDP à changer")}</div>}
                      </td>
                      <td className="muted-cell">{formatDate(u.lastLoginAt)}</td>
                      <td>
                        {me.role === 'SUPER_ADMIN' && <button type="button" className="tool-btn"
                          disabled={applyingSelection} onClick={(event) => { event.stopPropagation(); setHrUser(u); }}>
                          {t('Fiche RH')}
                        </button>}
                        {manageable ? (
                          <fieldset className="row-actions user-row-actions" disabled={applyingSelection} onClick={(event) => event.stopPropagation()}>
                            <button className="action-btn edit-btn" title={t("Modifier")} onClick={() => setModal(u)}><Icon name="edit" size={13} /></button>
                            <button className="action-btn key-btn" title={t("Réinitialiser le mot de passe")} onClick={() => resetPassword(u)}><Icon name="refresh" size={13} /></button>
                            <button className={`action-btn ${u.active ? 'warn-btn' : 'save-btn'}`} title={u.active ? t("Désactiver") : t("Activer")} onClick={() => toggleStatus(u)}><Icon name="power" size={13} /></button>
                            <button className="action-btn delete-btn" title={t("Supprimer")} onClick={() => remove(u)}><Icon name="trash" size={13} /></button>
                          </fieldset>
                        ) : <span className="muted-cell">—</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {filtered.length > shown.length && <div className="table-more">
            <span>{t("{0} affiché(s) sur {1}", { 0: shown.length, 1: filtered.length })}</span>
            <button type="button" className="tool-btn" onClick={() => setVisible((v) => v + PAGE_SIZE)}>{t("Afficher plus")}</button>
            <button type="button" className="tool-btn" onClick={() => setVisible(filtered.length)}>{t("Tout afficher")}</button>
          </div>}
        </div>
      </div>

      {modal && (
        <UserModal user={modal.id ? modal : null} roles={roles} onClose={() => setModal(null)} onSaved={handleSaved} />
      )}
      {hrUser && <UserHrProfileModal user={hrUser} onClose={() => setHrUser(null)} />}
      {hrImportOpen && <HrUpdateImportModal onClose={() => setHrImportOpen(false)} onImported={async (result) => {
        setNewRecruits(result.newRecruits.map(({ username, fullName }) => ({ username, fullName })));
        setSelectedIds(new Set());
        setUsers(await usersApi.list());
      }} />}
      {importOpen && <ImportExcelModal category={{ label: 'Utilisateurs' }} importFile={usersApi.importExcel}
        template={usersApi.template}
        instructions={t("Identifiant, nom complet, rôle et mot de passe temporaire obligatoires. Nouveaux comptes uniquement : les identifiants existants sont refusés. Changement de mot de passe obligatoire à la première connexion. Conservez le fichier de mots de passe de façon sécurisée puis supprimez-le après utilisation.")}
        onClose={() => setImportOpen(false)} onImported={handleImported} />}
    </>
  );
}
