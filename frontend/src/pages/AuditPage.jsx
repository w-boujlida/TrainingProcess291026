import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useState } from 'react';
import client, { errorMessage } from '../api/client';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';
import { ROLE_LABELS } from '../roles';

const ACTIONS = {
  CONNEXION: 'Connexion', DECONNEXION: 'Déconnexion', CONSULTATION: 'Consultation',
  CREATION: 'Création', MODIFICATION: 'Modification', SUPPRESSION: 'Suppression',
  SIGNATURE: 'Signature', IMPORT_EXCEL: 'Import Excel', EXPORT_EXCEL: 'Export Excel',
  MODELE_EXCEL: 'Modèle Excel', CHANGEMENT_MOT_DE_PASSE: 'Changement du mot de passe',
  REINITIALISATION_MOT_DE_PASSE: 'Réinitialisation du mot de passe', REQUETE: 'Requête',
};
const EMPTY_FILTERS = { username: '', ip: '', action: '', outcome: '', from: '', to: '' };

function deviceLabel(agent) {
  if (!agent) return t("Non renseigné");
  const browser = /Edg\//.test(agent) ? 'Edge' : /Firefox\//.test(agent) ? 'Firefox'
    : /Chrome\//.test(agent) ? 'Chrome' : /Safari\//.test(agent) ? 'Safari' : t("Autre navigateur");
  const os = /Android/.test(agent) ? 'Android' : /iPhone|iPad/.test(agent) ? 'iOS'
    : /Windows/.test(agent) ? 'Windows' : /Macintosh/.test(agent) ? 'macOS'
      : /Linux/.test(agent) ? 'Linux' : t("Système non identifié");
  return `${browser} · ${os}`;
}

function resourceLabel(path) {
  if (path.startsWith('/api/users')) return t("Comptes utilisateurs");
  if (path.startsWith('/api/training-sheets')) return t("Fiche de formation");
  if (path.startsWith('/api/workstation-forms')) return t("Fiche d’intégration");
  if (path.includes('/booklet')) return t("Livret d’intégration");
  if (path.startsWith('/api/recruitment')) return t("Recrutement");
  if (path.startsWith('/api/audit-events')) return t("Journal d’audit");
  if (path.startsWith('/api/auth')) return t("Authentification");
  return t("Autre requête");
}

export default function AuditPage() {
  useLocale();
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value));
    if (params.from) params.from = new Date(params.from).toISOString();
    if (params.to) params.to = new Date(params.to).toISOString();
    client.get('/audit-events', { params: { ...params, page, size: 50 } })
      .then(({ data }) => { if (!cancelled) setResult(data); })
      .catch((err) => { if (!cancelled) { setError(errorMessage(err)); setResult(null); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [filters, page, refresh]);

  const field = (name) => (event) => setDraft((current) => ({ ...current, [name]: event.target.value }));
  const apply = (event) => {
    event.preventDefault();
    if (draft.from && draft.to && new Date(draft.from) >= new Date(draft.to)) {
      setError(t("La date de fin doit être après la date de début."));
      return;
    }
    setPage(0);
    setFilters({ ...draft });
  };

  return <>
    <PageHeader title={t("Journal d’audit")} subtitle={t("Connexions, consultations et actions · Super Admin uniquement")} />
    <div className="content">
      <div className="alert alert-info"> {t("Historique conservé 90 jours, à partir de l’activation du journal. Le nom du poste est déclaré par l’utilisateur. L’adresse MAC n’est pas accessible depuis le navigateur. Les mots de passe et le contenu des signatures ne sont pas enregistrés.")} </div>
      <form className="audit-filters" onSubmit={apply}>
        <label className="form-group"><span className="form-label">{t("Identifiant exact")}</span>
          <input className="form-input" value={draft.username} maxLength={50} onChange={field('username')} placeholder={t("ex : a.dupont")} /></label>
        <label className="form-group"><span className="form-label">{t("Adresse IP exacte")}</span>
          <input className="form-input" value={draft.ip} maxLength={64} onChange={field('ip')} /></label>
        <label className="form-group"><span className="form-label">{t("Action")}</span>
          <select className="form-input" aria-label={t("Action")} value={draft.action} onChange={field('action')}>
            <option value="">{t("Toutes les actions")}</option>
            {Object.entries(ACTIONS).map(([key, value]) => <option key={key} value={key}>{t(value)}</option>)}
          </select></label>
        <label className="form-group"><span className="form-label">{t("Résultat")}</span>
          <select className="form-input" aria-label={t("Résultat")} value={draft.outcome} onChange={field('outcome')}>
            <option value="">{t("Tous")}</option><option value="SUCCESS">{t("Réussi")}</option><option value="FAILURE">{t("Échoué / refusé")}</option>
          </select></label>
        <label className="form-group"><span className="form-label">{t("Du (heure locale)")}</span>
          <input className="form-input" type="datetime-local" value={draft.from} onChange={field('from')} /></label>
        <label className="form-group"><span className="form-label">{t("Au (exclu, heure locale)")}</span>
          <input className="form-input" type="datetime-local" value={draft.to} onChange={field('to')} /></label>
        <div className="audit-filter-actions">
          <button className="btn btn-primary" disabled={loading}>{t("Filtrer")}</button>
          <button type="button" className="btn btn-secondary" disabled={loading}
            onClick={() => { setDraft(EMPTY_FILTERS); setFilters(EMPTY_FILTERS); setPage(0); setRefresh((value) => value + 1); }}>{t("Réinitialiser")}</button>
          <button type="button" className="btn btn-secondary" disabled={loading} onClick={() => setRefresh((value) => value + 1)}>
            <Icon name="refresh" size={14} />{t("Actualiser")}</button>
        </div>
      </form>
      {error && <div className="alert alert-error" role="alert">{error}</div>}
      <div className="table-container">
        <div className="table-header"><div className="table-title">{t("Événements")} <span className="table-count">{result?.total ?? '—'}</span></div></div>
        <div className="table-wrapper">
          <table className="data-table audit-table">
            <thead><tr><th>{t("Date / heure")}</th><th className="left">{t("Utilisateur")}</th><th className="left">{t("Action / tâche")}</th>
              <th>{t("Résultat")}</th><th className="left">{t("IP / poste")}</th></tr></thead>
            <tbody>
              {loading && <tr><td colSpan={5} className="table-empty"><span className="spinner" /></td></tr>}
              {!loading && result?.items.length === 0 && <tr><td colSpan={5} className="table-empty">{t("Aucun événement pour ces filtres.")}</td></tr>}
              {!loading && result?.items.map((entry) => <tr key={entry.id}>
                <td>{new Date(entry.occurredAt).toLocaleString(getLocale())}<small>#{entry.id}</small></td>
                <td className="left wrap"><strong>{entry.username || t("Non identifié")}</strong><small>{entry.role ? t(ROLE_LABELS[entry.role] || entry.role) : t("Tentative non authentifiée")}</small></td>
                <td className="left wrap"><strong>{t(ACTIONS[entry.action] || entry.action)}</strong>
                  <small>{resourceLabel(entry.resource)}</small><code>{entry.method} {entry.resource}</code>
                  {entry.affectedCount != null && <small>{entry.affectedCount} {t("traité(s),")} {entry.rejectedCount} {t("rejeté(s)")}</small>}
                </td>
                <td><span className={`status-badge ${entry.status < 400 ? 'status-active' : 'status-inactive'}`}>
                  {entry.status < 400 ? t("Réussi") : t("Échoué / refusé")}</span><small>HTTP {entry.status}</small></td>
                <td className="left wrap"><strong className="mono">{entry.ip}</strong>
                  <small>{entry.deviceName || t("Poste non renseigné")} {t("· nom déclaré")}</small>
                  <small title={entry.userAgent || ''}>{deviceLabel(entry.userAgent)}</small>
                  <details><summary>{t("Détails réseau / navigateur")}</summary>
                    <p>{t("Source IP :")} {entry.ipSource === 'PROXY' ? t("Proxy de confiance") : t("Connexion directe")} {t("· pair :")} {entry.peer}</p>
                    <p>{entry.userAgent || t("Non renseigné")}</p>
                  </details>
                </td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <div className="document-navigation audit-pagination">
          <button className="btn btn-secondary" disabled={loading || page === 0} onClick={() => setPage((value) => value - 1)}>{t("Précédent")}</button>
          <span>{t("Page")} {page + 1} / {Math.max(1, Math.ceil((result?.total || 0) / 50))}</span>
          <button className="btn btn-secondary" disabled={loading || !result || (page + 1) * 50 >= result.total}
            onClick={() => setPage((value) => value + 1)}>{t("Suivant")}</button>
        </div>
      </div>
    </div>
  </>;
}
