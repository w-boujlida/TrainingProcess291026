import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { CANDIDATE_STATUSES, RECRUITMENT_CATEGORIES } from '../roles';
import { dashboardApi } from '../api/dashboard.api';
import { errorMessage } from '../api/client';
import { bucketLabel, candidateMatrix, percent, projectMatrix, total } from '../dashboardData';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

const ROLE_INTRO = {
  SUPER_ADMIN: 'Vous disposez de tous les droits, y compris la gestion des administrateurs.',
  ADMIN: 'Vous gérez les comptes Superviseur, Formatrice et Team Leader.',
  SUPERVISEUR: 'Vous supervisez les sessions de formation et leurs résultats.',
  FORMATRICE: 'Vous animez et évaluez les sessions de formation.',
  TEAM_LEADER: 'Vous suivez la formation des membres de votre équipe.',
  QUALITY: 'Vous validez la conformité finale des parcours de formation.',
};
const STATUS_COLORS = { CANDIDAT: '#94a3b8', RECRUTE: '#3b82f6', EN_FORMATION: '#f59e0b', VALIDE: '#16a34a', REJETE: '#dc2626' };
const OUTCOME_COLORS = { ACQUIRED: '#16a34a', TO_REINFORCE: '#f59e0b', PENDING: '#94a3b8', OK: '#16a34a', NOK: '#dc2626', NA: '#cbd5e1' };

function Kpi({ title, value, hint, icon, color }) {
  return <div className={`stat-card ${color}`}>
    <div className="stat-header"><span className="stat-title">{t(title)}</span><span className="stat-icon"><Icon name={icon} size={18} /></span></div>
    <div className="stat-value">{value}</div>
    {hint && <div className="dash-hint">{hint}</div>}
  </div>;
}

function Card({ title, children, wide }) {
  return <section className={`dash-card${wide ? ' dash-wide' : ''}`}>
    <h3 className="dash-card-title">{t(title)}</h3>
    {children}
  </section>;
}

function Bars({ buckets = [], colors = {}, empty = 'Aucune donnée' }) {
  const max = Math.max(1, ...buckets.map((b) => b.count));
  const sum = total(buckets);
  if (!sum) return <p className="dash-empty">{t(empty)}</p>;
  return <ul className="dash-bars">
    {buckets.map((b) => <li key={b.key}>
      <span className="dash-bar-label" title={t(bucketLabel(b.key))}>{t(bucketLabel(b.key))}</span>
      <span className="dash-bar-track"><span className="dash-bar-fill"
        style={{ width: `${(b.count * 100) / max}%`, background: colors[b.key] }} /></span>
      <span className="dash-bar-value">{b.count} <small>{percent(b.count, sum)}%</small></span>
    </li>)}
  </ul>;
}

function Progress({ label, done, of }) {
  return <div className="dash-progress">
    <div className="dash-progress-head"><span>{t(label)}</span><strong>{done} / {of} · {percent(done, of)}%</strong></div>
    <div className="dash-bar-track"><span className="dash-bar-fill" style={{ width: `${percent(done, of)}%` }} /></div>
  </div>;
}

function MonthChart({ months }) {
  const max = Math.max(1, ...months.flatMap((m) => [m.hires, m.departures]));
  const label = (m) => new Date(`${m}-01T00:00:00`).toLocaleDateString(getLocale(), { month: 'short', year: '2-digit' });
  return <>
    <div className="dash-legend"><span className="dot dot-in" />{t('Embauches')}<span className="dot dot-out" />{t('Départs')}</div>
    <div className="dash-columns" role="img" aria-label={t('Embauches et départs par mois')}>
      {months.map((m) => <div key={m.month} className="dash-column">
        <div className="dash-column-bars">
          <span className="col-in" style={{ height: `${(m.hires * 100) / max}%` }} title={`${t('Embauches')} : ${m.hires}`} />
          <span className="col-out" style={{ height: `${(m.departures * 100) / max}%` }} title={`${t('Départs')} : ${m.departures}`} />
        </div>
        <span className="dash-column-values">{m.hires}/{m.departures}</span>
        <span className="dash-column-label">{label(m.month)}</span>
      </div>)}
    </div>
  </>;
}

function HrDashboard({ hr }) {
  return <>
    <div className="stats-grid">
      <Kpi title="Salariés actifs" value={hr.active} icon="userCheck" color="stat-green"
        hint={t('{0} fiches RH, {1} inactifs', { 0: hr.employees, 1: hr.inactive })} />
      {hr.byType.filter((b) => b.key !== 'NON_RENSEIGNE').map((b, i) =>
        <Kpi key={b.key} title={bucketLabel(b.key)} value={b.count} icon={['briefcase', 'tool', 'layers'][i] || 'users'}
          color={['stat-blue', 'stat-teal', 'stat-amber'][i] || 'stat-green'} hint={`${percent(b.count, hr.active)}% ${t('des actifs')}`} />)}
    </div>
    <div className="stats-grid">
      <Kpi title="Embauches du mois" value={hr.hiresThisMonth} icon="userPlus" color="stat-green" />
      <Kpi title="Départs du mois" value={hr.departuresThisMonth} icon="logout" color="stat-red" />
      <Kpi title="Femmes" value={`${percent(hr.gender.find((g) => g.key === 'FEMME')?.count || 0, hr.active)}%`} icon="user" color="stat-teal" />
      <Kpi title="CDI" value={`${percent(hr.contract.find((g) => g.key === 'CDI')?.count || 0, hr.active)}%`} icon="fileSheet" color="stat-blue" />
    </div>
    <div className="dash-grid">
      <Card title="Embauches et départs (12 derniers mois)" wide><MonthChart months={hr.months} /></Card>
      <Card title="Actifs par département"><Bars buckets={hr.byDepartment} /></Card>
      <Card title="Actifs par ligne de production"><Bars buckets={hr.byLine} /></Card>
      <Card title="Ancienneté"><Bars buckets={hr.seniority} /></Card>
      <Card title="Tranches d'âge"><Bars buckets={hr.age} /></Card>
      <Card title="Type de contrat"><Bars buckets={hr.contract} /></Card>
      <Card title="Homme / Femme"><Bars buckets={hr.gender} colors={{ FEMME: '#db2777', HOMME: '#2563eb' }} /></Card>
    </div>
  </>;
}

function FollowupDashboard({ f }) {
  const matrix = candidateMatrix(f.candidates);
  const dossiers = f.candidates.reduce((s, c) => s + c.count, 0);
  const inTraining = f.candidates.filter((c) => c.status === 'EN_FORMATION').reduce((s, c) => s + c.count, 0);
  const validated = f.candidates.filter((c) => c.status === 'VALIDE').reduce((s, c) => s + c.count, 0);
  return <>
    <div className="stats-grid">
      <Kpi title="Dossiers Recrutement" value={dossiers} icon="users" color="stat-green" />
      <Kpi title="En formation" value={inTraining} icon="graduation" color="stat-amber" />
      <Kpi title="Validés" value={validated} icon="checkCircle" color="stat-teal" />
      <Kpi title="Note moyenne des compétences" value={f.averageRating == null ? '—' : `${f.averageRating} / 5`}
        icon="book" color="stat-blue" hint={t('{0} compétence(s) notée(s)', { 0: f.ratedCompetencies })} />
    </div>
    <div className="dash-grid">
      <Card title="Dossiers par catégorie et statut" wide>
        <div className="table-wrapper"><table className="data-table dash-table">
          <thead><tr><th className="left">{t('Catégorie')}</th>
            {CANDIDATE_STATUSES.map((s) => <th key={s.code}>{t(s.label)}</th>)}<th>{t('Total')}</th></tr></thead>
          <tbody>{RECRUITMENT_CATEGORIES.map((c) => <tr key={c.code}>
            <td className="left"><Link to={`/recruitment/${c.slug}`}>{t(c.label)}</Link></td>
            {CANDIDATE_STATUSES.map((s) => <td key={s.code}>
              {matrix[c.code]?.[s.code] ? <span className="dash-pill" style={{ background: STATUS_COLORS[s.code] }}>{matrix[c.code][s.code]}</span> : '—'}
            </td>)}
            <td><strong>{matrix[c.code]?.total || 0}</strong></td>
          </tr>)}</tbody>
        </table></div>
      </Card>
      <Card title="Livret d'intégration">
        <Progress label="Signés par le salarié" done={f.bookletCandidateSigned} of={f.booklets} />
        <Progress label="Signés par la formatrice" done={f.bookletTrainerSigned} of={f.booklets} />
        <h4 className="dash-sub">{t('Résultat de l’évaluation')}</h4>
        <Bars buckets={f.bookletOutcome} colors={OUTCOME_COLORS} />
      </Card>
      <Card title="Objectifs du livret">
        <Bars buckets={f.objectives} colors={OUTCOME_COLORS} />
      </Card>
      <Card title="Fiche de formation">
        <Progress label="Visa superviseur" done={f.sheetSupervisorSigned} of={f.sheets} />
        <Progress label="Visa qualité" done={f.sheetQualitySigned} of={f.sheets} />
        <Progress label="Périodes signées par la formatrice" done={f.periodsTrainerSigned} of={f.periods} />
        <Progress label="Périodes signées par le team leader" done={f.periodsTeamLeaderSigned} of={f.periods} />
      </Card>
      <Card title="Notes des compétences (1 à 5)">
        <Bars buckets={f.ratings} empty="Aucune compétence notée" />
      </Card>
      <Card title="Fiche d'intégration au poste — étapes">
        <Bars buckets={f.workstationSteps} colors={OUTCOME_COLORS} />
      </Card>
    </div>
  </>;
}

const CATEGORY_SLUGS = Object.fromEntries(RECRUITMENT_CATEGORIES.map((c) => [c.code, c]));
const formatDay = (value) => (value ? new Date(`${value}T00:00:00`).toLocaleDateString(getLocale()) : '—');

function WorkforceDashboard({ w }) {
  const matrix = projectMatrix(w.projects, w.positions);
  const month = new Date(`${w.month}-01T00:00:00`).toLocaleDateString(getLocale(), { month: 'long', year: 'numeric' });
  const named = w.projects.filter((p) => p.project !== 'NON_RENSEIGNE');
  const seamstressesByProject = w.projects.filter((p) => p.seamstresses > 0)
    .map((p) => ({ key: p.project, count: p.seamstresses })).sort((a, b) => b.count - a.count);
  return <>
    <div className="stats-grid">
      <Kpi title="Dossiers Direct" value={w.direct} icon="tool" color="stat-blue" />
      <Kpi title="Couturières" value={w.seamstresses} icon="users" color="stat-teal" hint={`${percent(w.seamstresses, w.direct)}% ${t('des Directs')}`} />
      <Kpi title="Projets" value={named.length} icon="layers" color="stat-amber"
        hint={t('{0} poste(s) différent(s)', { 0: w.positions.filter((p) => p.key !== 'NON_RENSEIGNE').length })} />
      <Kpi title="Nouvelles recrues du mois" value={w.recruits.length} icon="userPlus" color="stat-green" hint={month} />
    </div>
    <div className="dash-grid">
      <Card title="Nombre par poste (dossiers Direct)"><Bars buckets={w.positions} /></Card>
      <Card title="Couturières par projet"><Bars buckets={seamstressesByProject} /></Card>
      <Card title="Postes par projet" wide>
        <div className="table-wrapper"><table className="data-table dash-table">
          <thead><tr><th className="left">{t('Projet')}</th>
            {matrix.columns.map((c) => <th key={c}>{t(bucketLabel(c))}</th>)}<th>{t('Total')}</th></tr></thead>
          <tbody>{matrix.rows.map((r) => <tr key={r.project}>
            <td className="left">
              <div className="cell-main">{t(bucketLabel(r.project))}</div>
              {r.lines.length > 1 && <div className="cell-sub">{r.lines.map((l) => `${t(bucketLabel(l.key))} ${l.count}`).join(' · ')}</div>}
            </td>
            {matrix.columns.map((c) => <td key={c}>{r.cells[c] ? r.cells[c] : <span className="muted-cell">—</span>}</td>)}
            <td><strong>{r.total}</strong></td>
          </tr>)}</tbody>
          <tfoot><tr><td className="left"><strong>{t('Total')}</strong></td>
            {matrix.columns.map((c) => <td key={c}><strong>{matrix.rows.reduce((s, r) => s + (r.cells[c] || 0), 0)}</strong></td>)}
            <td><strong>{w.direct}</strong></td></tr></tfoot>
        </table></div>
      </Card>
      <Card title="Nouvelles recrues du mois" wide>
        <div className="chip-group dash-recruit-cats">
          {w.recruitsByCategory.map((b) => <Link key={b.key} className="filter-chip" to={`/recruitment/${CATEGORY_SLUGS[b.key]?.slug || 'formation'}`}>
            {t(bucketLabel(b.key))} <span className="chip-count">{b.count}</span></Link>)}
        </div>
        {w.recruits.length === 0 ? <p className="dash-empty">{t('Aucune recrue ce mois-ci.')}</p> : (
          <div className="table-wrapper dash-recruits"><table className="data-table compact">
            <thead><tr>
              <th className="left">{t('Employé')}</th><th>{t('Catégorie')}</th><th className="left">{t('Poste')}</th>
              <th className="left">{t('Projet')}</th><th>{t("Date d'embauche")}</th><th>{t('Shift')}</th><th className="left">{t('Team leader')}</th>
            </tr></thead>
            <tbody>{w.recruits.map((r) => <tr key={r.candidateId}>
              <td className="left"><div className="cell-main">{r.fullName}</div><div className="cell-sub mono">{r.matricule || '—'}</div></td>
              <td>{t(bucketLabel(r.category))}</td>
              <td className="left">{r.position || '—'}</td>
              <td className="left"><div className="cell-main">{t(bucketLabel(r.project))}</div>{r.line && <div className="cell-sub">{r.line}</div>}</td>
              <td>{formatDay(r.hireDate)}</td>
              <td>{r.shiftLabel ? <span className="shift-chip">{t(r.shiftLabel)}</span> : <span className="muted-cell">—</span>}</td>
              <td className="left">{r.teamLeader || <span className="muted-cell">—</span>}</td>
            </tr>)}</tbody>
          </table></div>
        )}
      </Card>
    </div>
  </>;
}

function AccountsDashboard({ a }) {
  return <div className="stats-grid">
    <Kpi title="Total comptes" value={a.total} icon="users" color="stat-green" />
    <Kpi title="Connectés (7 jours)" value={a.loggedLast7Days} icon="clock" color="stat-teal" />
    <Kpi title="MDP à changer" value={a.mustChangePassword} icon="key" color="stat-amber"
      hint={t('{0} jamais connecté(s)', { 0: a.neverLogged })} />
    <Kpi title="Comptes verrouillés" value={a.locked} icon="lock" color="stat-red" />
  </div>;
}

export default function HomePage() {
  useLocale();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState(null);

  const load = () => {
    setError('');
    dashboardApi.get()
      .then((d) => { setData(d); setTab((current) => current || (d.hr ? 'hr' : 'followup')); })
      .catch((err) => setError(errorMessage(err)));
  };
  useEffect(load, []);

  const tabs = data ? [
    data.hr && ['hr', 'Effectifs (fiches RH)', 'users'],
    data.workforce && ['workforce', 'Couturières & postes', 'tool'],
    ['followup', 'Suivi des fiches de formation', 'graduation'],
    data.accounts && ['accounts', 'Comptes', 'key'],
  ].filter(Boolean) : [];

  return (
    <>
      <PageHeader title={t("Vue d'ensemble")} subtitle="Training Process — SAGE Automotive Interiors" />
      <div className="content">
        <div className="welcome-banner dash-welcome">
          <div>
            <div className="welcome-kicker">{t("Bienvenue")}</div>
            <h1 className="welcome-title">{user.fullName}</h1>
            <p className="welcome-text">{t(ROLE_INTRO[user.role])}</p>
          </div>
          <img src="/sage-logo.png" alt="" aria-hidden="true" className="welcome-logo" />
        </div>

        {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
        {!data && !error && <p className="dash-empty"><span className="spinner" /></p>}
        {data && <>
          <div className="dash-tabs" role="tablist">
            {tabs.map(([code, label, icon]) => <button key={code} type="button" role="tab" aria-selected={tab === code}
              className={`dash-tab${tab === code ? ' dash-tab-on' : ''}`} onClick={() => setTab(code)}>
              <Icon name={icon} size={14} />{t(label)}</button>)}
            <button type="button" className="tool-btn dash-refresh" onClick={load}><Icon name="refresh" size={13} />{t('Actualiser')}</button>
          </div>
          {tab === 'hr' && data.hr && <HrDashboard hr={data.hr} />}
          {tab === 'workforce' && data.workforce && <WorkforceDashboard w={data.workforce} />}
          {tab === 'followup' && <FollowupDashboard f={data.followup} />}
          {tab === 'accounts' && data.accounts && <AccountsDashboard a={data.accounts} />}
        </>}
      </div>
    </>
  );
}
