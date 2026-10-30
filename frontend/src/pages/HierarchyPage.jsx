import { t, useLocale } from '../i18n';
import { useEffect, useMemo, useState } from 'react';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { RECRUITMENT_EDITORS, SHIFTS } from '../roles';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';
import {
  NO_LEADER, NO_SHIFT, allSelected, assignmentChanges, filterHierarchy, leaderOptions, lineIds, supervisorIds, toggleIds,
} from '../hierarchyData';

const CATEGORIES = [['DIRECT', 'Direct'], ['INDIRECT', 'Indirect'], ['CADRE', 'Cadre'], ['', 'Tous']];

function ShiftChip({ label }) {
  return label ? <span className="shift-chip">{t(label)}</span> : <span className="muted-cell">—</span>;
}

function Check({ ids, selected, disabled, label, onToggle }) {
  return (
    <input type="checkbox" checked={allSelected(ids, selected)} disabled={disabled || ids.length === 0}
      aria-label={label} onChange={() => onToggle(ids)} />
  );
}

export default function HierarchyPage() {
  useLocale();
  const { user: me } = useAuth();
  const canEdit = RECRUITMENT_EDITORS.includes(me.role);
  const [category, setCategory] = useState('DIRECT');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [missingLeader, setMissingLeader] = useState(false);
  const [missingShift, setMissingShift] = useState(false);
  const [open, setOpen] = useState(new Set());
  const [selected, setSelected] = useState(new Set());
  const [bulkShift, setBulkShift] = useState('');
  const [bulkLeader, setBulkLeader] = useState('');
  const [busy, setBusy] = useState(false);

  const load = (cat = category) => {
    setLoading(true); setError('');
    return recruitmentApi.hierarchy(cat)
      .then(setData)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    setSelected(new Set()); setOpen(new Set()); setNotice('');
    load(category);
  }, [category]);

  const supervisors = useMemo(() => filterHierarchy(data?.supervisors || [], { search, missingLeader, missingShift }),
    [data, search, missingLeader, missingShift]);
  const leaderNames = useMemo(() => new Map((data?.teamLeaders || []).map((l) => [l.id, l.fullName])), [data]);
  const options = useMemo(() => leaderOptions(data?.supervisors || [], selected, data?.teamLeaders || []), [data, selected]);
  const filtering = Boolean(search.trim()) || missingLeader || missingShift;
  const isOpen = (key) => filtering || open.has(key);
  const toggle = (ids) => setSelected((current) => toggleIds(current, ids));
  const toggleOpen = (key) => setOpen((current) => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });

  const apply = async () => {
    const changes = assignmentChanges(bulkShift, bulkLeader);
    if (!selected.size || !Object.keys(changes).length) return;
    const parts = [];
    if (changes.changeShift) parts.push(t("shift « {0} »", { 0: changes.shift ? t(SHIFTS.find((s) => s.code === changes.shift).label) : t("Sans shift") }));
    if (changes.changeTeamLeader) parts.push(t("team leader « {0} »", { 0: changes.teamLeaderId ? leaderNames.get(changes.teamLeaderId) : t("Aucun") }));
    if (!window.confirm(t("Affecter {0} à {1} dossier(s) ?", { 0: parts.join(' + '), 1: selected.size }))) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const updated = await recruitmentApi.assign([...selected], changes);
      setNotice(t("{0} dossier(s) mis à jour : {1}.", { 0: updated.length, 1: parts.join(' + ') }));
      setSelected(new Set()); setBulkShift(''); setBulkLeader('');
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t("Hiérarchie")} subtitle={t("Superviseur → Team leader → Couturières, regroupées par ligne selon la fiche RH")} />
      <div className="content">
        <div className="stats-grid">
          <div className="stat-card stat-green">
            <div className="stat-header"><span className="stat-title">{t("Superviseurs")}</span><span className="stat-icon"><Icon name="userCheck" size={18} /></span></div>
            <div className="stat-value">{data?.supervisors.length ?? '—'}</div>
          </div>
          <div className="stat-card stat-blue">
            <div className="stat-header"><span className="stat-title">{t("Employés")}</span><span className="stat-icon"><Icon name="users" size={18} /></span></div>
            <div className="stat-value">{data?.members ?? '—'}</div>
          </div>
          <button type="button" className={`stat-card stat-filter stat-amber${missingLeader ? ' selected' : ''}`} onClick={() => setMissingLeader((v) => !v)}>
            <div className="stat-header"><span className="stat-title">{t("Sans team leader")}</span><span className="stat-icon"><Icon name="alert" size={18} /></span></div>
            <div className="stat-value">{data?.withoutTeamLeader ?? '—'}</div>
          </button>
          <button type="button" className={`stat-card stat-filter stat-red${missingShift ? ' selected' : ''}`} onClick={() => setMissingShift((v) => !v)}>
            <div className="stat-header"><span className="stat-title">{t("Sans shift")}</span><span className="stat-icon"><Icon name="clock" size={18} /></span></div>
            <div className="stat-value">{data?.withoutShift ?? '—'}</div>
          </button>
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {notice && <div className="alert alert-success"><Icon name="checkCircle" size={14} />{notice}</div>}

        <div className="table-container">
          <div className="table-header">
            <div className="chip-group" role="group" aria-label={t("Catégorie")}>
              {CATEGORIES.map(([code, label]) => (
                <button type="button" key={code || 'ALL'} aria-pressed={category === code}
                  className={`filter-chip${category === code ? ' filter-chip-on' : ''}`} onClick={() => setCategory(code)}>{t(label)}</button>
              ))}
            </div>
            <div className="table-controls">
              <div className="search-container">
                <span className="search-icon"><Icon name="search" size={14} /></span>
                <input className="search-input" placeholder={t("Superviseur, team leader, ligne, nom…")} value={search}
                  onChange={(e) => setSearch(e.target.value)} />
              </div>
              <button type="button" className="tool-btn" disabled={filtering}
                onClick={() => setOpen(open.size ? new Set() : new Set((data?.supervisors || []).map((s) => s.name || '')))}>
                {open.size ? t("Tout replier") : t("Tout déplier")}
              </button>
              <button type="button" className="tool-btn" onClick={() => load()} disabled={loading}>
                <Icon name="refresh" size={13} />{t("Actualiser")}
              </button>
            </div>
          </div>

          {canEdit && (
            <div className="user-selection-toolbar hier-toolbar" aria-busy={busy}>
              <span role="status">{t("{0} sélectionné(s)", { 0: selected.size })}</span>
              <select className="filter-select" aria-label={t("Shift à affecter")} value={bulkShift} disabled={busy || !selected.size}
                onChange={(e) => setBulkShift(e.target.value)}>
                <option value="">{t("Shift : inchangé")}</option>
                {SHIFTS.map((s) => <option key={s.code} value={s.code}>{t(s.label)}</option>)}
                <option value={NO_SHIFT}>{t("Sans shift")}</option>
              </select>
              <select className="filter-select" aria-label={t("Team leader à affecter")} value={bulkLeader} disabled={busy || !selected.size}
                onChange={(e) => setBulkLeader(e.target.value)}>
                <option value="">{t("Team leader : inchangé")}</option>
                {options.nearby.length > 0 && (
                  <optgroup label={t("Team leaders du superviseur")}>
                    {options.nearby.map((l) => <option key={l.userId} value={l.userId}>{l.fullName}{l.line ? ` · ${l.line}` : ''}{l.team ? ` · ${l.team}` : ''}</option>)}
                  </optgroup>
                )}
                <optgroup label={t("Tous les team leaders")}>
                  {options.others.map((l) => <option key={l.id} value={l.id}>{l.fullName}</option>)}
                </optgroup>
                <option value={NO_LEADER}>{t("Aucun team leader")}</option>
              </select>
              <button type="button" className="tool-btn" disabled={busy || !selected.size || (!bulkShift && !bulkLeader)} onClick={apply}>
                {busy ? <span className="btn-spinner dark" /> : <Icon name="check" size={13} />}{t("Affecter")}
              </button>
              {selected.size > 0 && <button type="button" className="tool-btn" disabled={busy} onClick={() => setSelected(new Set())}>{t("Tout désélectionner")}</button>}
            </div>
          )}

          {loading && !data && <div className="table-empty"><span className="spinner" /></div>}
          {!loading && supervisors.length === 0 && <div className="table-empty">{t("Aucun employé trouvé.")}</div>}

          <div className="hier-list">
            {supervisors.map((s) => {
              const key = s.name || '';
              const ids = supervisorIds(s);
              const expanded = isOpen(key);
              return (
                <section key={key} className="hier-supervisor">
                  <div className="hier-supervisor-head">
                    {canEdit && <Check ids={ids} selected={selected} disabled={busy} onToggle={toggle}
                      label={t("Sélectionner toute l’équipe de {0}", { 0: s.name || t("Sans superviseur") })} />}
                    <button type="button" className="hier-expand" aria-expanded={expanded} onClick={() => toggleOpen(key)} disabled={filtering}>
                      <Icon name="chevron" size={13} className={`nav-chevron${expanded ? ' open' : ''}`} />
                      <span className="hier-role">{t("Superviseur")}</span>
                      <strong>{s.name || t("Sans superviseur")}</strong>
                    </button>
                    <span className="hier-badges">
                      <span className="chip-count">{t("{0} employé(s)", { 0: s.members })}</span>
                      <span className="chip-count">{t("{0} team leader(s)", { 0: s.teamLeaders.length })}</span>
                      {s.withoutTeamLeader > 0 && <span className="chip-count hier-warn">{t("{0} sans team leader", { 0: s.withoutTeamLeader })}</span>}
                    </span>
                  </div>

                  {expanded && (
                    <div className="hier-body">
                      {s.teamLeaders.length > 0 && (
                        <div className="hier-leaders">
                          {s.teamLeaders.map((l) => (
                            <div key={l.userId} className={`hier-leader${l.assignable ? '' : ' hier-leader-off'}`}>
                              {canEdit && l.candidateId && <Check ids={[l.candidateId]} selected={selected} disabled={busy} onToggle={toggle}
                                label={t("Sélectionner {0}", { 0: l.fullName })} />}
                              <div>
                                <div className="hier-leader-name"><span className="hier-role">{t("Team leader")}</span> {l.fullName}</div>
                                <div className="cell-sub">{[l.line, l.team && t("Équipe {0}", { 0: l.team })].filter(Boolean).join(' · ') || '—'}
                                  {' · '}{t("{0} employé(s)", { 0: l.members })}</div>
                                {!l.assignable && <div className="cell-sub hier-warn">{t("Rôle du compte à passer en « Team Leader » pour l’affecter")}</div>}
                              </div>
                              <ShiftChip label={l.shiftLabel} />
                            </div>
                          ))}
                        </div>
                      )}

                      {s.lines.map((line) => (
                        <div key={line.line || ''} className="hier-line">
                          <div className="hier-line-head">
                            {canEdit && <Check ids={lineIds(line)} selected={selected} disabled={busy} onToggle={toggle}
                              label={t("Sélectionner la ligne {0}", { 0: line.line || t("Sans ligne") })} />}
                            <span className="hier-role">{t("Ligne")}</span>
                            <strong>{line.line || t("Sans ligne")}</strong>
                            <span className="chip-count">{line.members.length}</span>
                          </div>
                          <table className="data-table compact">
                            <thead>
                              <tr>
                                {canEdit && <th />}
                                <th className="left">{t("Employé")}</th><th className="left">{t("Poste")}</th><th>{t("Équipe")}</th>
                                <th>{t("Shift")}</th><th className="left">{t("Team leader")}</th>
                              </tr>
                            </thead>
                            <tbody>
                              {line.members.map((m) => (
                                <tr key={m.candidateId} className={selected.has(m.candidateId) ? 'user-selected-row' : undefined}>
                                  {canEdit && <td><Check ids={[m.candidateId]} selected={selected} disabled={busy} onToggle={toggle}
                                    label={t("Sélectionner {0}", { 0: m.fullName })} /></td>}
                                  <td className="left">
                                    <div className="cell-main">{m.fullName}</div>
                                    <div className="cell-sub mono">{m.matricule}</div>
                                  </td>
                                  <td className="left">{m.position || '—'}</td>
                                  <td>{m.team || '—'}</td>
                                  <td><ShiftChip label={m.shiftLabel} /></td>
                                  <td className="left">
                                    {m.teamLeaderId ? (
                                      <>
                                        <div className="cell-main">{m.teamLeaderName}</div>
                                        <div className="cell-sub">{m.teamLeaderAuto ? t("Automatique (fiche RH)") : t("Affecté manuellement")}</div>
                                      </>
                                    ) : (
                                      <>
                                        <span className="muted-cell">—</span>
                                        {m.suggestedTeamLeaderId && <div className="cell-sub">{t("Suggéré : {0}", { 0: leaderNames.get(m.suggestedTeamLeaderId) || '' })}</div>}
                                      </>
                                    )}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        </div>
      </div>
    </>
  );
}
