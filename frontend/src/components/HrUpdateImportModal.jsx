import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { t, useLocale } from '../i18n';
import { hrImportApi } from '../api/hrImport';
import { credentialsCsv } from '../hrImportCsv';
import { errorMessage } from '../api/client';

export default function HrUpdateImportModal({ onClose, onImported }) {
  useLocale();
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [downloaded, setDownloaded] = useState(false);
  const dialog = useRef(null);
  const canClose = !busy && (!result?.created || downloaded);
  useEffect(() => {
    const previous = document.activeElement;
    dialog.current?.focus();
    return () => previous?.focus();
  }, []);
  useEffect(() => {
    if (!result?.created || downloaded) return;
    const warn = (event) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [result, downloaded]);
  const pick = (event) => {
    const chosen = event.target.files[0];
    setError(''); setPreview(null); setFile(null);
    if (!chosen) return;
    if (!chosen.name.toLowerCase().endsWith('.xlsx') || chosen.size > 10 * 1024 * 1024) {
      setError(t('Sélectionnez un fichier .xlsx de 10 Mo maximum.'));
      return;
    }
    setFile(chosen);
  };
  const analyze = async () => {
    setBusy(true); setError(''); setPreview(null);
    try { setPreview(await hrImportApi.preview(file)); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const apply = async () => {
    setBusy(true); setError('');
    let response;
    try {
      response = await hrImportApi.apply(file, preview.confirmation);
      setResult(response);
    } catch (err) {
      setError(errorMessage(err) + ' ' + t('Actualisez l’aperçu pour vérifier le résultat avant de réessayer.'));
      setPreview(null);
    } finally { setBusy(false); }
    if (response) {
      try { await onImported(response); }
      catch (err) { setError(t('Import enregistré, mais la liste n’a pas pu être actualisée.') + ' ' + errorMessage(err)); }
    }
  };
  const download = () => {
    const url = URL.createObjectURL(new Blob([credentialsCsv(result.newRecruits)], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = 'acces_nouvelles_recrues.csv';
    document.body.appendChild(anchor); anchor.click(); anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setDownloaded(true);
  };
  const keys = (event) => {
    if (event.key === 'Escape' && canClose) onClose();
    if (event.key !== 'Tab') return;
    const items = [...dialog.current.querySelectorAll('button:not(:disabled),input:not(:disabled),a')];
    if (!items.length) { event.preventDefault(); return; }
    const first = items[0], last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  };
  const recruits = result?.newRecruits || preview?.newRecruits || [];
  return <div className="modal-backdrop">
    <section className="modal-card hr-import-modal" role="dialog" aria-modal="true" aria-labelledby="hr-import-title"
      tabIndex={-1} ref={dialog} onKeyDown={keys}>
      <div className="modal-head">
        <h3 id="hr-import-title">{t('Importer les mises à jour RH')}</h3>
        <button type="button" className="modal-close" onClick={onClose} disabled={!canClose} aria-label={t('Fermer')}>×</button>
      </div>
      <div className="modal-body-pad modal-scroll">
        <p>{t('Feuille RH, 62 colonnes sélectionnées. Les cellules vides conservent les valeurs existantes. Aucun compte supprimé, aucun rôle ni mot de passe existant modifié.')}</p>
        <p className="muted">{t('Les comptes de gestion et de validation sont protégés et ne seront pas modifiés.')}</p>
        {error && <div role="alert" className="alert alert-error">{error}</div>}
        {!result && <>
          <label className="form-group">
            <span className="form-label">{t('Fichier RH mensuel (.xlsx)')}</span>
            <input type="file" accept=".xlsx" disabled={busy} onChange={pick} />
          </label>
          <button type="button" className="btn btn-secondary" disabled={!file || busy} onClick={analyze}>{t('Afficher l’aperçu')}</button>
        </>}
        {busy && <p role="status"><span className="btn-spinner dark" /> {t('Traitement en cours. Ne fermez pas cette fenêtre.')}</p>}
        {(preview || result) && <div className="import-summary">
          <div className="import-sum sum-blue"><span>{result?.updated ?? preview.updates}</span>{t('Mis à jour')}</div>
          <div className="import-sum sum-green"><span>{result?.created ?? preview.creates}</span>{t('Nouveaux comptes')}</div>
          <div className="import-sum sum-grey"><span>{result?.protectedAccounts ?? preview.protectedAccounts}</span>{t('Comptes protégés ignorés')}</div>
        </div>}
        {preview && !result && <>
          <p>{t('Erreurs Excel conservées : {0}. Formules sans résultat : {1} (Non calculé dans Excel).',
            { 0: preview.excelErrors, 1: preview.uncalculated })}</p>
          {preview.errors.length > 0 && <div role="alert" className="alert alert-error">
            {t('Corrigez les lignes en erreur avant de confirmer l’import.')} ({preview.errors.length})
            <ul>{preview.errors.slice(0, 200).map((issue) => <li key={`${issue.row}:${issue.message}`}>{t('Ligne')} {issue.row} : {t(issue.message)}</li>)}</ul>
          </div>}
        </>}
        {result && <div role="status" className="alert alert-success">{t('Import enregistré. Les comptes existants non présents dans le fichier sont conservés.')}</div>}
        {recruits.length > 0 && <section className="hr-import-recruits">
          <h4>{t('Nouvelles recrues à traiter dans Recrutement')} ({recruits.length})</h4>
          <p>{t('Aucun dossier ni workflow créé automatiquement. Les nouveaux comptes Employé n’ont pas accès aux modules.')}</p>
          <ul>{recruits.map((user) => <li key={user.username}><span className="mono">{user.username}</span> — {user.fullName} · {user.active ? t('Actif') : t('Inactif')}</li>)}</ul>
        </section>}
        {result?.created > 0 && <>
          <p role="alert">{t('Téléchargez les mots de passe temporaires avant de fermer. Ils ne seront plus affichés après cette opération. Conservez ce fichier dans un emplacement privé.')}</p>
          <button type="button" className="btn btn-primary" onClick={download}>{t('Télécharger les accès des nouvelles recrues')}</button>
        </>}
      </div>
      <div className="modal-foot">
        {result && canClose && <Link className="btn btn-secondary" to="/recruitment/formation" onClick={onClose}>{t('Ouvrir Recrutement')}</Link>}
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={!canClose}>{t(result ? 'Terminer' : 'Annuler')}</button>
        {preview && !result && <button type="button" className="btn btn-primary" disabled={busy || preview.errors.length > 0}
          onClick={apply}>{t('Confirmer la mise à jour et les créations')}</button>}
      </div>
    </section>
  </div>;
}
