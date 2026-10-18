import { useEffect, useMemo, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { CANDIDATE_STATUSES, RECRUITMENT_CATEGORIES, RECRUITMENT_EDITORS, SHIFTS } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

const EMPTY_FORM = {
  matricule: '', fullName: '', cin: '', birthDate: '', phone: '', email: '', address: '',
  position: '', department: '', hireDate: '', educationLevel: '', shift: '',
  trainerId: '', teamLeaderId: '', status: 'CANDIDAT', remarks: '',
};

const STATUS_STATS = [
  { code: 'CANDIDAT', label: 'Candidats', color: 'stat-blue', icon: 'user' },
  { code: 'RECRUTE', label: 'Recrutés', color: 'stat-teal', icon: 'userCheck' },
  { code: 'EN_FORMATION', label: 'En formation', color: 'stat-amber', icon: 'graduation' },
  { code: 'VALIDE', label: 'Validés', color: 'stat-green', icon: 'checkCircle' },
  { code: 'REJETE', label: 'Rejetés', color: 'stat-red', icon: 'xCircle' },
];

function formatDate(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : '—';
}

function initials(name) {
  return name.split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

function toForm(c) {
  if (!c) return EMPTY_FORM;
  const form = { ...EMPTY_FORM };
  Object.keys(EMPTY_FORM).forEach((k) => { if (c[k] != null) form[k] = c[k]; });
  form.trainerId = c.trainer?.id ?? '';
  form.teamLeaderId = c.teamLeader?.id ?? '';
  return form;
}

function toPayload(form, category) {
  const payload = { category };
  Object.entries(form).forEach(([k, v]) => { payload[k] = v === '' ? null : v; });
  payload.trainerId = form.trainerId ? Number(form.trainerId) : null;
  payload.teamLeaderId = form.teamLeaderId ? Number(form.teamLeaderId) : null;
  return payload;
}

function CandidateModal({ candidate, category, staff, readOnly, onClose, onSaved }) {
  const isEdit = Boolean(candidate);
  const [form, setForm] = useState(() => toForm(candidate));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const trainers = staff.filter((s) => s.role === 'FORMATRICE');
  const leaders = staff.filter((s) => s.role === 'TEAM_LEADER');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (readOnly) return;
    setError('');
    setSaving(true);
    try {
      const payload = toPayload(form, category.code);
      const saved = isEdit
        ? await recruitmentApi.update(candidate.id, payload)
        : await recruitmentApi.create(payload);
      onSaved(saved, isEdit);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const field = (key, label, { type = 'text', required = false, mono = false, placeholder = '' } = {}) => (
    <div className="form-group">
      <label className={`form-label${required ? ' label-required' : ''}`} htmlFor={`c-${key}`}>{label}</label>
      <input id={`c-${key}`} type={type} className={`form-input${mono ? ' mono' : ''}`} value={form[key]}
        onChange={set(key)} required={required} placeholder={readOnly ? '' : placeholder} />
    </div>
  );

  const title = readOnly ? 'Fiche candidat' : isEdit ? 'Modifier le candidat' : `Nouveau candidat — ${category.label}`;

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className="modal-card modal-wide" onSubmit={handleSubmit} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div className="modal-head-title">
            <Icon name={readOnly ? 'eye' : isEdit ? 'edit' : 'userPlus'} size={17} />{title}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Fermer"><Icon name="x" size={18} /></button>
        </div>

        <fieldset className="modal-body-pad modal-scroll" disabled={readOnly}>
          {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}

          <div className="form-section-title"><Icon name="user" size={14} />Identité</div>
          <div className="form-grid-3">
            {field('matricule', 'Matricule', { mono: true, placeholder: 'ex : 10452' })}
            {field('fullName', 'Nom et prénom', { required: true, placeholder: 'Nom Prénom' })}
            {field('cin', 'CIN', { required: true, mono: true, placeholder: 'ex : 09876543' })}
            {field('birthDate', 'Date de naissance', { type: 'date' })}
            {field('phone', 'Téléphone', { type: 'tel', placeholder: 'ex : 22 123 456' })}
            {field('email', 'E-mail', { type: 'email', placeholder: 'prenom.nom@mail.com' })}
          </div>
          {field('address', 'Adresse', { placeholder: 'Adresse complète' })}

          <div className="form-section-title"><Icon name="briefcase" size={14} />Poste</div>
          <div className="form-grid-3">
            {field('position', 'Poste / fonction', { placeholder: 'ex : Opératrice couture' })}
            {field('department', 'Département / ligne', { placeholder: 'ex : Couture L3' })}
            {field('hireDate', 'Date de recrutement', { type: 'date' })}
            {field('educationLevel', "Niveau d'étude / diplôme", { placeholder: 'ex : Bac, BTS…' })}
            <div className="form-group">
              <label className="form-label" htmlFor="c-shift">Shift</label>
              <select id="c-shift" className="form-input" value={form.shift} onChange={set('shift')}>
                <option value="">—</option>
                {SHIFTS.map((s) => <option key={s.code} value={s.code}>{s.label}</option>)}
              </select>
            </div>
          </div>

          <div className="form-section-title"><Icon name="graduation" size={14} />Affectation &amp; suivi</div>
          <div className="form-grid-3">
            <div className="form-group">
              <label className="form-label" htmlFor="c-trainer">Formatrice affectée</label>
              <select id="c-trainer" className="form-input" value={form.trainerId} onChange={set('trainerId')}>
                <option value="">—</option>
                {candidate?.trainer && !trainers.some((t) => t.id === candidate.trainer.id) && (
                  <option value={candidate.trainer.id}>{candidate.trainer.fullName}</option>
                )}
                {trainers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="c-leader">Team leader affecté</label>
              <select id="c-leader" className="form-input" value={form.teamLeaderId} onChange={set('teamLeaderId')}>
                <option value="">—</option>
                {candidate?.teamLeader && !leaders.some((t) => t.id === candidate.teamLeader.id) && (
                  <option value={candidate.teamLeader.id}>{candidate.teamLeader.fullName}</option>
                )}
                {leaders.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label label-required" htmlFor="c-status">Statut</label>
              <select id="c-status" className="form-input" value={form.status} onChange={set('status')} required>
                {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{s.label}</option>)}
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="c-remarks">Remarques</label>
            <textarea id="c-remarks" className="form-input form-textarea" rows={3} value={form.remarks}
              onChange={set('remarks')} maxLength={2000} />
          </div>
        </fieldset>

        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>{readOnly ? 'Fermer' : 'Annuler'}</button>
          {!readOnly && (
            <button className="btn btn-primary" disabled={saving}>
              {saving ? <span className="btn-spinner" /> : <Icon name="check" strokeWidth={2.5} />}
              Enregistrer
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

export default function RecruitmentPage() {
  const { category: slug } = useParams();
  const category = RECRUITMENT_CATEGORIES.find((c) => c.slug === slug);
  const { user: me } = useAuth();
  const canEdit = RECRUITMENT_EDITORS.includes(me.role);

  const [candidates, setCandidates] = useState([]);
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [modal, setModal] = useState(null);

  useEffect(() => {
    if (!category) return;
    let cancelled = false;
    setLoading(true); setError(''); setNotice(''); setSearch(''); setStatusFilter(''); setModal(null);
    Promise.all([recruitmentApi.list(category.code), recruitmentApi.staff()])
      .then(([c, s]) => { if (!cancelled) { setCandidates(c); setStaff(s); } })
      .catch((err) => { if (!cancelled) setError(errorMessage(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [category?.code]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return candidates.filter((c) =>
      (!statusFilter || c.status === statusFilter)
      && (!q || [c.fullName, c.cin, c.matricule, c.position, c.department, c.phone]
        .some((v) => (v || '').toLowerCase().includes(q))));
  }, [candidates, search, statusFilter]);

  const counts = useMemo(() => {
    const map = Object.fromEntries(CANDIDATE_STATUSES.map((s) => [s.code, 0]));
    candidates.forEach((c) => { map[c.status] += 1; });
    return map;
  }, [candidates]);

  if (!category) return <Navigate to="/recruitment/cadre" replace />;

  const replace = (updated) => setCandidates((list) => list.map((c) => (c.id === updated.id ? updated : c)));

  const run = async (fn) => {
    setError(''); setNotice('');
    try { await fn(); } catch (err) { setError(errorMessage(err)); }
  };

  const changeStatus = (c, status) => run(async () => {
    const updated = await recruitmentApi.setStatus(c.id, status);
    replace(updated);
    setNotice(`Statut de ${c.fullName} : ${updated.statusLabel}`);
  });

  const remove = (c) => {
    if (!window.confirm(`Supprimer définitivement le candidat ${c.fullName} ?`)) return;
    run(async () => {
      await recruitmentApi.remove(c.id);
      setCandidates((list) => list.filter((x) => x.id !== c.id));
      setNotice(`Candidat supprimé : ${c.fullName}`);
    });
  };

  const handleSaved = (saved, isEdit) => {
    if (isEdit) replace(saved);
    else setCandidates((list) => [saved, ...list]);
    setNotice(isEdit ? `Candidat modifié : ${saved.fullName}` : `Candidat ajouté : ${saved.fullName}`);
    setModal(null);
  };

  return (
    <>
      <PageHeader title={`Recrutement — ${category.label}`} subtitle="Suivi des candidats et des recrutements" />
      <div className="content">
        <div className="stats-grid stats-grid-6">
          <div className="stat-card stat-green">
            <div className="stat-header"><span className="stat-title">Total</span><span className="stat-icon"><Icon name={category.icon} size={18} /></span></div>
            <div className="stat-value">{candidates.length}</div>
          </div>
          {STATUS_STATS.map((s) => (
            <button key={s.code} type="button"
              className={`stat-card stat-filter ${s.color}${statusFilter === s.code ? ' selected' : ''}`}
              onClick={() => setStatusFilter((f) => (f === s.code ? '' : s.code))}>
              <div className="stat-header"><span className="stat-title">{s.label}</span><span className="stat-icon"><Icon name={s.icon} size={18} /></span></div>
              <div className="stat-value">{counts[s.code]}</div>
            </button>
          ))}
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {notice && <div className="alert alert-success"><Icon name="checkCircle" size={14} />{notice}</div>}

        <div className="table-container">
          <div className="table-header">
            <div className="table-title">Candidats {category.label} <span className="table-count">{filtered.length}</span></div>
            <div className="table-controls">
              <div className="search-container">
                <span className="search-icon"><Icon name="search" size={14} /></span>
                <input className="search-input" placeholder="Nom, CIN, matricule…" value={search} onChange={(e) => setSearch(e.target.value)} />
              </div>
              <select className="filter-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="">Tous les statuts</option>
                {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{s.label}</option>)}
              </select>
              {canEdit && (
                <button className="add-btn" onClick={() => setModal({ mode: 'create' })}>
                  <Icon name="plus" size={13} strokeWidth={2.5} />Nouveau candidat
                </button>
              )}
            </div>
          </div>

          <div className="table-wrapper">
            <table className="data-table compact">
              <thead>
                <tr>
                  <th className="left">Candidat</th><th>Matricule</th><th className="left">Poste / Département</th>
                  <th>Date recrut.</th><th>Shift</th><th className="left">Formatrice / TL</th><th>Statut</th><th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan="8" className="table-empty"><span className="spinner" /></td></tr>}
                {!loading && filtered.length === 0 && <tr><td colSpan="8" className="table-empty">Aucun candidat trouvé.</td></tr>}
                {!loading && filtered.map((c) => (
                  <tr key={c.id}>
                    <td className="left">
                      <div className="user-cell">
                        <div className={`user-avatar cand-${c.status}`}>{initials(c.fullName)}</div>
                        <div>
                          <div className="user-cell-name">{c.fullName}</div>
                          <div className="user-cell-mail mono">CIN {c.cin}{c.phone ? ` · ${c.phone}` : ''}</div>
                        </div>
                      </div>
                    </td>
                    <td className="mono">{c.matricule || '—'}</td>
                    <td className="left wrap">
                      <div className="cell-main">{c.position || '—'}</div>
                      <div className="cell-sub">{c.department || ''}</div>
                    </td>
                    <td className="muted-cell">{formatDate(c.hireDate)}</td>
                    <td>{c.shiftLabel ? <span className="shift-chip">{c.shiftLabel}</span> : <span className="muted-cell">—</span>}</td>
                    <td className="left wrap">
                      <div className="cell-main">{c.trainer?.fullName || '—'}</div>
                      <div className="cell-sub">{c.teamLeader?.fullName || ''}</div>
                    </td>
                    <td>
                      {canEdit ? (
                        <select className={`status-select cand-${c.status}`} value={c.status}
                          onChange={(e) => changeStatus(c, e.target.value)} aria-label="Statut">
                          {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{s.label}</option>)}
                        </select>
                      ) : <span className={`cand-badge cand-${c.status}`}>{c.statusLabel}</span>}
                    </td>
                    <td>
                      <div className="row-actions">
                        {canEdit ? (
                          <>
                            <button className="action-btn edit-btn" title="Modifier" onClick={() => setModal({ mode: 'edit', candidate: c })}><Icon name="edit" size={13} /></button>
                            <button className="action-btn delete-btn" title="Supprimer" onClick={() => remove(c)}><Icon name="trash" size={13} /></button>
                          </>
                        ) : (
                          <button className="action-btn key-btn" title="Voir la fiche" onClick={() => setModal({ mode: 'view', candidate: c })}><Icon name="eye" size={13} /></button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {modal && (
        <CandidateModal candidate={modal.candidate} category={category} staff={staff}
          readOnly={modal.mode === 'view'} onClose={() => setModal(null)} onSaved={handleSaved} />
      )}
    </>
  );
}
