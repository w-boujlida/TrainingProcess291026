import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { Navigate, useParams } from 'react-router-dom';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { CANDIDATE_STATUSES, RECRUITMENT_CATEGORIES, RECRUITMENT_EDITORS, SHIFTS, TRAINING_LEVELS } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';
import ImportExcelModal from '../components/ImportExcelModal';
import IntegrationBookletModal from '../components/IntegrationBookletModal';
import TrainingSheetModal from '../components/TrainingSheetModal';
import WorkstationFormModal from '../components/WorkstationFormModal';

const EMPTY_FORM = {
  matricule: '', fullName: '', cin: '', birthDate: '', phone: '', email: '', address: '',
  position: '', department: '', hireDate: '', educationLevel: '', shift: '',
  trainerId: '', teamLeaderId: '', status: 'CANDIDAT', remarks: '', trainingLevel: '',
};

/** Mirrors backend TrainingLevel.trialEnd (java.time.Period semantics: month added first, day clamped). */
function trialEnd(hireDate, level) {
  if (!hireDate || !level) return '';
  const [y, m, d] = hireDate.split('-').map(Number);
  let date;
  if (level === 'APPRENTIE') {
    const lastDay = new Date(y, m + 1, 0).getDate();
    date = new Date(y, m, Math.min(d, lastDay) + 15);
  } else {
    date = new Date(y, m - 1, d + 21);
  }
  return date.toLocaleDateString(getLocale());
}

const STATUS_STATS = [
  { code: 'CANDIDAT', label: 'Candidats', color: 'stat-blue', icon: 'user' },
  { code: 'RECRUTE', label: 'Recrutés', color: 'stat-teal', icon: 'userCheck' },
  { code: 'EN_FORMATION', label: 'En formation', color: 'stat-amber', icon: 'graduation' },
  { code: 'VALIDE', label: 'Validés', color: 'stat-green', icon: 'checkCircle' },
  { code: 'REJETE', label: 'Rejetés', color: 'stat-red', icon: 'xCircle' },
];

function formatDate(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString(getLocale()) : '—';
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

/**
 * Focused choice pop-up: first step for Direct (position) and Formation (level) when adding,
 * or the Direct position when a Formation employee is validated (transfer to Direct).
 */
function PreStepModal({ category, positions, transfer, busy, onClose, onContinue }) {
  useLocale();
  const isLevel = Boolean(category.hasLevel) && !transfer;
  const [value, setValue] = useState(() => (transfer && positions.includes(transfer.position) ? transfer.position : ''));
  const [query, setQuery] = useState('');
  const options = isLevel
    ? TRAINING_LEVELS
    : positions
      .filter((p) => `${p} ${t(p)}`.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')
        .includes(query.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')))
      .map((p) => ({ code: p, label: p }));
  const next = (v = value) => { if (v && !busy) onContinue(isLevel ? { trainingLevel: v } : { position: v }); };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className={`modal-card ${isLevel ? 'modal-md' : 'modal-wide'}`} onMouseDown={(e) => e.stopPropagation()}
        onSubmit={(e) => { e.preventDefault(); next(); }}>
        <div className="modal-head">
          <div className="modal-head-title">
            <Icon name={transfer ? 'checkCircle' : isLevel ? 'graduation' : 'briefcase'} size={17} />
            {transfer ? t("Valider et passer en Direct — {0}", { 0: transfer.fullName }) : t("Nouveau candidat — {0}", { 0: t(category.label) })}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label={t("Fermer")}><Icon name="x" size={18} /></button>
        </div>
        <div className="modal-body-pad">
          <div className="prestep-head">
            <span className="prestep-badge">{transfer ? t("Formation → Direct") : t("Étape 1 / 2")}</span>
            <div>
              <div className="prestep-title">{isLevel ? t("Choisir le niveau") : transfer ? t("Choisir le poste Direct") : t("Choisir le poste")}</div>
              <div className="prestep-sub">
                {transfer
                  ? t("{0} est validée en Formation et sera transférée automatiquement en Direct.", { 0: transfer.fullName })
                  : isLevel ? t("Le niveau détermine la durée de la période d'essai.") : t("Sélectionnez le poste avant de remplir la fiche.")}
              </div>
            </div>
          </div>

          {!isLevel && (
            <div className="search-container prestep-search">
              <span className="search-icon"><Icon name="search" size={14} /></span>
              <input className="search-input" placeholder={t("Rechercher un poste…")} value={query} autoFocus
                onChange={(e) => setQuery(e.target.value)} />
            </div>
          )}

          <div className={isLevel ? 'choice-grid choice-grid-3' : 'choice-grid choice-grid-pos'} role="radiogroup">
            {options.map((o) => {
              const checked = value === o.code;
              return (
                <label key={o.code} className={`choice-card${checked ? ' checked' : ''}${o.color ? ` ${o.color}` : ''}`}
                  onDoubleClick={() => next(o.code)}>
                  <input type="checkbox" checked={checked} onChange={() => setValue(checked ? '' : o.code)}
                    aria-label={t(o.label)} />
                  <span className="choice-check"><Icon name="check" size={12} strokeWidth={3} /></span>
                  {isLevel ? (
                    <span className="choice-body">
                      <span className="choice-icon"><Icon name={o.icon} size={20} /></span>
                      <span className="choice-label">{t(o.label)}</span>
                      <span className="choice-trial"><Icon name="clock" size={12} />{t("Période d'essai :")} {t(o.trial)}</span>
                      <span className="choice-hint">{t(o.hint)}</span>
                    </span>
                  ) : (
                    <span className="choice-label">{t(o.label)}</span>
                  )}
                </label>
              );
            })}
            {!isLevel && options.length === 0 && <div className="empty-hint">{t("Aucun poste ne correspond.")}</div>}
          </div>
        </div>
        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>{t("Annuler")}</button>
          <button type="submit" className="btn btn-primary" disabled={!value || busy}>
            {transfer
              ? <>{busy ? <span className="btn-spinner" /> : <Icon name="check" size={14} strokeWidth={2.5} />}{t("Valider → Direct")}</>
              : <>{t("Continuer")}<Icon name="arrowRight" size={14} strokeWidth={2.5} /></>}
          </button>
        </div>
      </form>
    </div>
  );
}

function CandidateModal({ candidate, preset, category, staff, positions, directPositions = [], readOnly, onClose, onSaved }) {
  useLocale();
  const isEdit = Boolean(candidate);
  const fixedPositions = positions.length > 0;
  const hasLevel = Boolean(category.hasLevel);
  const [form, setForm] = useState(() => {
    const f = { ...toForm(candidate), ...preset };
    // A legacy free-text position must be replaced by a value from the list.
    if (fixedPositions && !readOnly && f.position && !positions.includes(f.position)) f.position = '';
    return f;
  });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const trainers = staff.filter((s) => s.role === 'FORMATRICE');
  const leaders = staff.filter((s) => s.role === 'TEAM_LEADER');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (readOnly) return;
    if (hasLevel && !form.trainingLevel) { setError(t("Choisissez le niveau : Apprentie, Junior ou Confirmée.")); return; }
    setError('');
    setSaving(true);
    try {
      const payload = toPayload(form, category.code);
      delete payload.directPosition;
      if (hasLevel && form.status === 'VALIDE') payload.position = form.directPosition;
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
      <label className={`form-label${required ? ' label-required' : ''}`} htmlFor={`c-${key}`}>{t(label)}</label>
      <input id={`c-${key}`} type={type} className={`form-input${mono ? ' mono' : ''}`} value={form[key]}
        onChange={set(key)} required={required} placeholder={readOnly ? '' : placeholder} />
    </div>
  );

  const title = readOnly ? t("Fiche candidat") : isEdit ? t("Modifier le candidat")
    : t("Nouveau candidat — {0}{1}", { 0: t(category.label), 1: preset ? t(' · Étape 2 / 2') : '' });

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className="modal-card modal-wide" onSubmit={handleSubmit} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div className="modal-head-title">
            <Icon name={readOnly ? 'eye' : isEdit ? 'edit' : 'userPlus'} size={17} />{t(title)}
          </div>
          <button type="button" className="modal-close" onClick={onClose} aria-label={t("Fermer")}><Icon name="x" size={18} /></button>
        </div>

        <fieldset className="modal-body-pad modal-scroll" disabled={readOnly}>
          {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}

          {fixedPositions && (
            <div className="position-picker">
              <label className="form-label label-required" htmlFor="c-position">
                <Icon name="briefcase" size={14} />{t("Poste")} {t(category.label)}
              </label>
              <select id="c-position" className="form-input" value={form.position} onChange={set('position')} required autoFocus={!isEdit && !preset?.position}>
                <option value="">{t("— Choisir le poste —")}</option>
                {form.position && !positions.includes(form.position) && <option value={form.position}>{form.position}</option>}
                {positions.map((p) => <option key={p} value={p}>{t(p)}</option>)}
              </select>
            </div>
          )}

          {hasLevel && (
            <div className="position-picker">
              <div className="form-label label-required"><Icon name="graduation" size={14} />{t("Niveau")}</div>
              <div className="level-inline" role="radiogroup" aria-label={t("Niveau")}>
                {TRAINING_LEVELS.map((l) => (
                  <label key={l.code} className={`level-chip ${l.color}${form.trainingLevel === l.code ? ' checked' : ''}`}>
                    <input type="checkbox" checked={form.trainingLevel === l.code}
                      onChange={() => setForm((f) => ({ ...f, trainingLevel: l.code }))} />
                    <span className="choice-check"><Icon name="check" size={11} strokeWidth={3} /></span>
                    <span>{t(l.label)}</span><small>{t(l.trial)}</small>
                  </label>
                ))}
              </div>
              {form.trainingLevel && (
                <div className="trial-info">
                  <Icon name="clock" size={13} /> {t("Période d'essai :")} <strong>{TRAINING_LEVELS.find((l) => l.code === form.trainingLevel)?.trial}</strong>
                  {form.hireDate
                    ? <> {t("· fin prévue le")} <strong>{trialEnd(form.hireDate, form.trainingLevel)}</strong></>
                    : <> {t("· renseignez la date de recrutement pour calculer la fin")}</>}
                </div>
              )}
            </div>
          )}

          <div className="form-section-title"><Icon name="user" size={14} />{t("Identité")}</div>
          <div className="form-grid-3">
            {field('matricule', t("Matricule"), { mono: true, placeholder: t("ex : 10452") })}
            {field('fullName', t("Nom et prénom"), { required: true, placeholder: t("Nom Prénom") })}
            {field('cin', 'CIN', { required: true, mono: true, placeholder: t("ex : 09876543") })}
            {field('birthDate', t("Date de naissance"), { type: 'date' })}
            {field('phone', t("Téléphone"), { type: 'tel', placeholder: t("ex : 22 123 456") })}
            {field('email', 'E-mail', { type: 'email', placeholder: 'prenom.nom@mail.com' })}
          </div>
          {field('address', t("Adresse"), { placeholder: t("Adresse complète") })}

          <div className="form-section-title"><Icon name="briefcase" size={14} />{t("Poste")}</div>
          <div className="form-grid-3">
            {!fixedPositions && field('position', t("Poste / fonction"), { placeholder: t("ex : Opératrice couture") })}
            {field('department', t("Département / ligne"), { placeholder: t("ex : Couture L3") })}
            {field('hireDate', t("Date de recrutement"), { type: 'date' })}
            {field('educationLevel', t("Niveau d'étude / diplôme"), { placeholder: t("ex : Bac, BTS…") })}
            <div className="form-group">
              <label className="form-label" htmlFor="c-shift">{t("Shift")}</label>
              <select id="c-shift" className="form-input" value={form.shift} onChange={set('shift')}>
                <option value="">—</option>
                {SHIFTS.map((s) => <option key={s.code} value={s.code}>{t(s.label)}</option>)}
              </select>
            </div>
          </div>

          <div className="form-section-title"><Icon name="graduation" size={14} />{t("Affectation & suivi")}</div>
          <div className="form-grid-3">
            <div className="form-group">
              <label className="form-label" htmlFor="c-trainer">{t("Formatrice affectée")}</label>
              <select id="c-trainer" className="form-input" value={form.trainerId} onChange={set('trainerId')}>
                <option value="">—</option>
                {candidate?.trainer && !trainers.some((t) => t.id === candidate.trainer.id) && (
                  <option value={candidate.trainer.id}>{candidate.trainer.fullName}</option>
                )}
                {trainers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="c-leader">{t("Team leader affecté")}</label>
              <select id="c-leader" className="form-input" value={form.teamLeaderId} onChange={set('teamLeaderId')}>
                <option value="">—</option>
                {candidate?.teamLeader && !leaders.some((t) => t.id === candidate.teamLeader.id) && (
                  <option value={candidate.teamLeader.id}>{candidate.teamLeader.fullName}</option>
                )}
                {leaders.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label label-required" htmlFor="c-status">{t("Statut")}</label>
              <select id="c-status" className="form-input" value={form.status} onChange={set('status')} required>
                {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{t(s.label)}</option>)}
              </select>
            </div>
          </div>
          {hasLevel && form.status === 'VALIDE' && !readOnly && (
            <div className="position-picker transfer-box">
              <label className="form-label label-required" htmlFor="c-directPosition">
                <Icon name="arrowRight" size={14} />{t("Validée → transfert automatique en Direct : poste Direct")} </label>
              <select id="c-directPosition" className="form-input" value={form.directPosition || ''} required
                onChange={set('directPosition')}>
                <option value="">{t("— Choisir le poste Direct —")}</option>
                {directPositions.map((p) => <option key={p} value={p}>{t(p)}</option>)}
              </select>
            </div>
          )}
          <div className="form-group">
            <label className="form-label" htmlFor="c-remarks">{t("Remarques")}</label>
            <textarea id="c-remarks" className="form-input form-textarea" rows={3} value={form.remarks}
              onChange={set('remarks')} maxLength={2000} />
          </div>
        </fieldset>

        <div className="modal-foot">
          <button type="button" className="btn btn-secondary" onClick={onClose}>{readOnly ? t("Fermer") : t("Annuler")}</button>
          {!readOnly && (
            <button className="btn btn-primary" disabled={saving}>
              {saving ? <span className="btn-spinner" /> : <Icon name="check" strokeWidth={2.5} />} {t("Enregistrer")} </button>
          )}
        </div>
      </form>
    </div>
  );
}

export default function RecruitmentPage() {
  const locale = useLocale();
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
  const [bookletCandidate, setBookletCandidate] = useState(null);
  const [trainingSheetCandidate, setTrainingSheetCandidate] = useState(null);
  const [workstationCandidate, setWorkstationCandidate] = useState(null);
  const [importOpen, setImportOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [positionsMap, setPositionsMap] = useState({});
  const [positionFilter, setPositionFilter] = useState('');
  const [levelFilter, setLevelFilter] = useState('');
  const [transferring, setTransferring] = useState(false);
  const positions = positionsMap[category?.code] || [];
  const directPositions = positionsMap.DIRECT || [];

  useEffect(() => {
    recruitmentApi.positions().then(setPositionsMap).catch(() => {});
  }, []);

  useEffect(() => {
    if (!category) return;
    let cancelled = false;
    setLoading(true); setError(''); setNotice(''); setSearch(''); setStatusFilter(''); setPositionFilter(''); setLevelFilter(''); setModal(null); setBookletCandidate(null); setTrainingSheetCandidate(null); setWorkstationCandidate(null); setImportOpen(false);
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
      && (!positionFilter || c.position === positionFilter)
      && (!levelFilter || c.trainingLevel === levelFilter)
      && (!q || [c.fullName, c.cin, c.matricule, c.position, positions.includes(c.position) ? t(c.position) : '', c.department, c.phone]
        .some((v) => (v || '').toLowerCase().includes(q))));
  }, [candidates, search, statusFilter, positionFilter, levelFilter, positions, locale.language]);

  const counts = useMemo(() => {
    const map = Object.fromEntries(CANDIDATE_STATUSES.map((s) => [s.code, 0]));
    candidates.forEach((c) => { map[c.status] += 1; });
    return map;
  }, [candidates]);

  if (!category) return <Navigate to="/recruitment/formation" replace />;

  const replace = (updated) => setCandidates((list) => list.map((c) => (c.id === updated.id ? updated : c)));

  const run = async (fn) => {
    setError(''); setNotice('');
    try { await fn(); } catch (err) { setError(errorMessage(err)); }
  };

  const changeStatus = (c, status) => {
    if (category.hasLevel && status === 'VALIDE') { setModal({ mode: 'transfer', candidate: c }); return; }
    run(async () => {
      const updated = await recruitmentApi.setStatus(c.id, status);
      replace(updated);
      setNotice(t("Statut de {0} : {1}", { 0: c.fullName, 1: t(updated.statusLabel) }));
    });
  };

  const transferNotice = (saved) => t("{0} validée en Formation → transférée en Direct (poste : {1}).", { 0: saved.fullName, 1: t(saved.position) });
  const confirmTransfer = (c, position) => run(async () => {
    setTransferring(true);
    try {
      const updated = await recruitmentApi.setStatus(c.id, 'VALIDE', position);
      setCandidates((list) => list.filter((x) => x.id !== c.id));
      setModal(null);
      setNotice(transferNotice(updated));
    } finally { setTransferring(false); }
  });

  const remove = (c) => {
    if (!window.confirm(t("Supprimer définitivement le candidat {0} ?", { 0: c.fullName }))) return;
    run(async () => {
      await recruitmentApi.remove(c.id);
      setCandidates((list) => list.filter((x) => x.id !== c.id));
      setNotice(t("Candidat supprimé : {0}", { 0: c.fullName }));
    });
  };

  const handleExport = () => run(async () => {
    setExporting(true);
    try { await recruitmentApi.exportExcel(category.code); } finally { setExporting(false); }
  });

  const handleImported = (res) => {
    setNotice(t("Import {0} : {1} créé(s), {2} mis à jour, {3} en erreur.", { 0: t(category.label), 1: res.created, 2: res.updated, 3: res.skipped }));
    recruitmentApi.list(category.code).then(setCandidates).catch((err) => setError(errorMessage(err)));
  };

  const handleSaved = (saved, isEdit) => {
    if (saved.category !== category.code) {
      setCandidates((list) => list.filter((x) => x.id !== saved.id));
      setNotice(transferNotice(saved));
      setModal(null);
      return;
    }
    if (isEdit) replace(saved);
    else setCandidates((list) => [saved, ...list]);
    setNotice(isEdit ? t("Candidat modifié : {0}", { 0: saved.fullName }) : t("Candidat ajouté : {0}", { 0: saved.fullName }));
    setModal(null);
  };

  return (
    <>
      <PageHeader title={t("Employés — {0}", { 0: t(category.label) })} subtitle={t("Recrutement · suivi des candidats et des recrutements")} />
      <div className="content">
        <div className="stats-grid stats-grid-6">
          <div className="stat-card stat-green">
            <div className="stat-header"><span className="stat-title">{t("Total")}</span><span className="stat-icon"><Icon name={category.icon} size={18} /></span></div>
            <div className="stat-value">{candidates.length}</div>
          </div>
          {STATUS_STATS.map((s) => (
            <button key={s.code} type="button"
              className={`stat-card stat-filter ${s.color}${statusFilter === s.code ? ' selected' : ''}`}
              onClick={() => setStatusFilter((f) => (f === s.code ? '' : s.code))}>
              <div className="stat-header"><span className="stat-title">{t(s.label)}</span><span className="stat-icon"><Icon name={s.icon} size={18} /></span></div>
              <div className="stat-value">{counts[s.code]}</div>
            </button>
          ))}
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {notice && <div className="alert alert-success"><Icon name="checkCircle" size={14} />{notice}</div>}

        <div className="table-container">
          <div className="table-header">
            <div className="table-title">{t("Candidats")} {t(category.label)} <span className="table-count">{filtered.length}</span></div>
            <div className="table-controls">
              <div className="search-container">
                <span className="search-icon"><Icon name="search" size={14} /></span>
                <input className="search-input" placeholder={t("Nom, CIN, matricule…")} value={search} onChange={(e) => setSearch(e.target.value)} />
              </div>
              <select className="filter-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="">{t("Tous les statuts")}</option>
                {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{t(s.label)}</option>)}
              </select>
              {positions.length > 0 && (
                <select className="filter-select" value={positionFilter} onChange={(e) => setPositionFilter(e.target.value)} aria-label={t("Filtrer par poste")}>
                  <option value="">{t("Tous les postes")}</option>
                  {positions.map((p) => <option key={p} value={p}>{t(p)}</option>)}
                </select>
              )}
              {category.hasLevel && (
                <select className="filter-select" value={levelFilter} onChange={(e) => setLevelFilter(e.target.value)} aria-label={t("Filtrer par niveau")}>
                  <option value="">{t("Tous les niveaux")}</option>
                  {TRAINING_LEVELS.map((l) => <option key={l.code} value={l.code}>{t(l.label)}</option>)}
                </select>
              )}
              <button className="tool-btn" onClick={handleExport} disabled={exporting || loading} title={t("Exporter la liste en Excel")}>
                {exporting ? <span className="btn-spinner dark" /> : <Icon name="download" size={14} />}{t("Exporter")} </button>
              {canEdit && (
                <button className="tool-btn" onClick={() => setImportOpen(true)} title={t("Import en masse depuis Excel")}>
                  <Icon name="upload" size={14} />{t("Importer")} </button>
              )}
              {canEdit && (
                <button className="add-btn" onClick={() => setModal({ mode: (category.hasLevel || positions.length) ? 'pick' : 'create' })}>
                  <Icon name="plus" size={13} strokeWidth={2.5} />{t("Nouveau candidat")} </button>
              )}
            </div>
          </div>

          <div className="table-wrapper">
            <table className="data-table compact">
              <thead>
                <tr>
                  <th className="left">{t("Candidat")}</th><th>{t("Matricule")}</th><th className="left">{t("Poste / Département")}</th>
                  <th>{t("Date recrut.")}</th><th>{t("Shift")}</th><th className="left">{t("Formatrice / TL")}</th><th>{t("Statut")}</th><th>{t("Actions")}</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan="8" className="table-empty"><span className="spinner" /></td></tr>}
                {!loading && filtered.length === 0 && <tr><td colSpan="8" className="table-empty">{t("Aucun candidat trouvé.")}</td></tr>}
                {!loading && filtered.map((c) => (
                  <tr key={c.id}>
                    <td className="left">
                      <div className="user-cell">
                        <div className={`user-avatar cand-${c.status}`}>{initials(c.fullName)}</div>
                        <div>
                          <div className="user-cell-name">{c.fullName}</div>
                          <div className="user-cell-mail mono">{t("CIN")} {c.cin}{c.phone ? ` · ${c.phone}` : ''}</div>
                        </div>
                      </div>
                    </td>
                    <td className="mono">{c.matricule || '—'}</td>
                    <td className="left wrap">
                      <div className="cell-main">{positions.includes(c.position) ? t(c.position) : c.position || '—'}</div>
                      <div className="cell-sub">{c.department || ''}</div>
                      {!category.hasLevel && c.formationValidatedOn && (
                        <div className="level-row">
                          <span className="level-badge from-formation" title={t("Validée en Formation le {0}", { 0: formatDate(c.formationValidatedOn) })}> {t("Issue de Formation")}{t(c.trainingLevelLabel) ? ` · ${t(c.trainingLevelLabel)}` : ''}
                          </span>
                        </div>
                      )}
                      {category.hasLevel && c.trainingLevel && (
                        <div className="level-row">
                          <span className={`level-badge ${TRAINING_LEVELS.find((l) => l.code === c.trainingLevel)?.color || ''}`}>{t(c.trainingLevelLabel)}</span>
                          {c.trialEndDate && <span className="cell-sub" title={t("Fin de période d'essai")}>{t("fin essai")} {formatDate(c.trialEndDate)}</span>}
                        </div>
                      )}
                    </td>
                    <td className="muted-cell">{formatDate(c.hireDate)}</td>
                    <td>{t(c.shiftLabel) ? <span className="shift-chip">{t(c.shiftLabel)}</span> : <span className="muted-cell">—</span>}</td>
                    <td className="left wrap">
                      <div className="cell-main">{c.trainer?.fullName || '—'}</div>
                      <div className="cell-sub">{c.teamLeader?.fullName || ''}</div>
                    </td>
                    <td>
                      {canEdit ? (
                        <select className={`status-select cand-${c.status}`} value={c.status}
                          onChange={(e) => changeStatus(c, e.target.value)} aria-label={t("Statut")}>
                          {CANDIDATE_STATUSES.map((s) => <option key={s.code} value={s.code}>{t(s.label)}</option>)}
                        </select>
                      ) : <span className={`cand-badge cand-${c.status}`}>{t(c.statusLabel)}</span>}
                    </td>
                    <td>
                      <div className="row-actions">
                        <button className="action-btn training-sheet-action" title={t("Fiche de formation")}
                          aria-label={t("Fiche de formation de {0}", { 0: c.fullName })}
                          onClick={() => setTrainingSheetCandidate(c)}>
                          <Icon name="graduation" size={13} />
                        </button>
                        <button className="action-btn workstation-action" title={t("Fiche d’intégration et de formation au poste")}
                          aria-label={t("Fiche d’intégration de {0}", { 0: c.fullName })} onClick={() => setWorkstationCandidate(c)}>
                          <Icon name="fileSheet" size={13} />
                        </button>
                        <button className="action-btn booklet-action" title={t("Livret d’intégration")}
                          aria-label={t("Livret d’intégration de {0}", { 0: c.fullName })}
                          onClick={() => setBookletCandidate(c)}>
                          <Icon name="book" size={13} />
                        </button>
                        {canEdit ? (
                          <>
                            <button className="action-btn edit-btn" title={t("Modifier")} onClick={() => setModal({ mode: 'edit', candidate: c })}><Icon name="edit" size={13} /></button>
                            <button className="action-btn delete-btn" title={t("Supprimer")} onClick={() => remove(c)}><Icon name="trash" size={13} /></button>
                          </>
                        ) : (
                          <button className="action-btn key-btn" title={t("Voir la fiche")} onClick={() => setModal({ mode: 'view', candidate: c })}><Icon name="eye" size={13} /></button>
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

      {modal?.mode === 'pick' && (
        <PreStepModal category={category} positions={positions} onClose={() => setModal(null)}
          onContinue={(preset) => setModal({ mode: 'create', preset })} />
      )}
      {modal?.mode === 'transfer' && (
        <PreStepModal category={category} positions={directPositions} transfer={modal.candidate} busy={transferring}
          onClose={() => setModal(null)} onContinue={({ position }) => confirmTransfer(modal.candidate, position)} />
      )}
      {modal && modal.mode !== 'pick' && modal.mode !== 'transfer' && (
        <CandidateModal candidate={modal.candidate} preset={modal.preset} category={category} staff={staff} positions={positions}
          directPositions={directPositions}
          readOnly={modal.mode === 'view'} onClose={() => setModal(null)} onSaved={handleSaved} />
      )}
      {importOpen && (
        <ImportExcelModal category={category} onClose={() => setImportOpen(false)} onImported={handleImported} />
      )}
      {bookletCandidate && (
        createPortal(
          <IntegrationBookletModal candidateId={bookletCandidate.id} fixedPosition={positions.includes(bookletCandidate.position)} canEdit={canEdit} user={me}
            onClose={() => setBookletCandidate(null)} />,
          document.body,
        )
      )}
      {trainingSheetCandidate && (
        createPortal(
          <TrainingSheetModal candidateId={trainingSheetCandidate.id} fixedPosition={positions.includes(trainingSheetCandidate.position)} user={me}
            onClose={() => setTrainingSheetCandidate(null)} />,
          document.body,
        )
      )}
      {workstationCandidate && createPortal(
        <WorkstationFormModal candidateId={workstationCandidate.id} fixedPosition={positions.includes(workstationCandidate.position)} user={me}
          onClose={() => setWorkstationCandidate(null)} />,
        document.body,
      )}
    </>
  );
}
