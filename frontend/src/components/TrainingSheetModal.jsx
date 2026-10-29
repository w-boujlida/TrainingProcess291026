import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useRef, useState } from 'react';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import Icon from './Icon';

const RATINGS = ['Faible', 'Moyen', 'Satisfaisant', 'Bien', 'Excellent'];
const SECTIONS = [
  { code: 'BEHAVIOR', key: 'behavior', label: 'COMPORTEMENT' },
  { code: 'KNOWLEDGE', key: 'knowledge', label: 'CONNAISSANCE' },
  { code: 'MACHINE', key: 'machine', label: 'MACHINE' },
  { code: 'SEWING', key: 'sewing', label: 'COUTURE' },
];

function formatDate(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString(getLocale()) : '—';
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString(getLocale(), { dateStyle: 'short', timeStyle: 'short' }) : '';
}

function isApproved(period) {
  return Boolean(period.trainerSignedAt || period.teamLeaderSignedAt);
}

function payloadFor(sheet, periods, opinions) {
  return {
    version: sheet.version,
    periods: periods.map((period) => ({
      id: period.id,
      startDate: period.startDate || null,
      endDate: period.endDate || null,
      competencies: period.competencies.map((competency) => ({
        id: competency.id,
        rating: competency.rating === '' || competency.rating == null ? null : Number(competency.rating),
        comment: competency.comment?.trim() || null,
      })),
    })),
    transferOpinions: Object.fromEntries(SECTIONS.map(({ key }) => [key, opinions[key]?.trim() || null])),
  };
}

export default function TrainingSheetModal({ candidateId, fixedPosition, user, onClose }) {
  useLocale();
  const [sheet, setSheet] = useState(null);
  const [periods, setPeriods] = useState([]);
  const [opinions, setOpinions] = useState({});
  const [activeWeek, setActiveWeek] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const bodyRef = useRef(null);
  const isAssignedTrainer = user.role === 'FORMATRICE' && sheet?.trainerId === user.id;
  const isAssignedLeader = user.role === 'TEAM_LEADER' && sheet?.teamLeaderId === user.id;
  const canEdit = ['SUPER_ADMIN', 'ADMIN'].includes(user.role) || isAssignedTrainer || isAssignedLeader;
  const dirty = sheet && JSON.stringify(payloadFor(sheet, periods, opinions))
    !== JSON.stringify(payloadFor(sheet, sheet.periods, sheet.transferOpinions));

  const applySheet = (data) => {
    setSheet(data);
    setPeriods(data.periods.map((period) => ({
      ...period,
      startDate: period.startDate || '',
      endDate: period.endDate || '',
      competencies: period.competencies.map((competency) => ({
        ...competency, rating: competency.rating ?? '', comment: competency.comment || '',
      })),
    })));
    setOpinions(data.transferOpinions);
  };

  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 });
  }, [activeWeek]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    recruitmentApi.getTrainingSheet(candidateId)
      .then((data) => { if (!cancelled) applySheet(data); })
      .catch((err) => { if (!cancelled) setError(errorMessage(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [candidateId]);

  useEffect(() => {
    const warnUnsaved = (event) => {
      if (dirty) { event.preventDefault(); event.returnValue = ''; }
    };
    window.addEventListener('beforeunload', warnUnsaved);
    return () => window.removeEventListener('beforeunload', warnUnsaved);
  }, [dirty]);

  const close = () => {
    if (busy) return;
    if (dirty && !window.confirm(t("Fermer sans enregistrer les modifications ?"))) return;
    onClose();
  };

  const patchPeriod = (index, patch) => setPeriods((current) => (
    current.map((period, i) => (i === index ? { ...period, ...patch } : period))
  ));

  const patchCompetency = (periodIndex, competencyIndex, patch) => setPeriods((current) => (
    current.map((period, i) => i !== periodIndex ? period : ({
      ...period,
      competencies: period.competencies.map((competency, j) => (
        j === competencyIndex ? { ...competency, ...patch } : competency
      )),
    }))
  ));

  const runAction = async (action, message) => {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      applySheet(await action());
      setNotice(message);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const save = () => {
    if (!dirty) return;
    const hasSignatures = sheet.periods.some(isApproved) || sheet.supervisorSignedAt || sheet.qualitySignedAt;
    if (hasSignatures && !window.confirm(
      t("Les modifications annuleront les validations des semaines modifiées et suivantes, ainsi que les validations finales. ")
      + t("Une modification des avis seuls annule uniquement les validations finales. Continuer ?"),
    )) return;
    runAction(() => recruitmentApi.updateTrainingSheet(candidateId, payloadFor(sheet, periods, opinions)),
      t("Fiche enregistrée."));
  };

  const signWeek = (period) => runAction(
    () => (isAssignedTrainer
      ? recruitmentApi.signTrainingPeriodTrainer(candidateId, period.id)
      : recruitmentApi.signTrainingPeriodLeader(candidateId, period.id)),
    t("{0} validée.", { 0: t(period.title) }),
  );

  const approvedCount = sheet?.periods.filter(isApproved).length || 0;
  const status = sheet?.qualitySignedAt ? t("Validée par Qualité")
    : sheet?.supervisorSignedAt ? t("En attente de Qualité")
      : approvedCount === 5 ? t("En attente du Superviseur") : t("{0} / 5 semaines validées", { 0: approvedCount });

  return (
    <div className="modal-backdrop training-sheet-backdrop" onMouseDown={close}>
      <section className="modal-card training-sheet-modal document-wizard" role="dialog" aria-modal="true"
        aria-labelledby="training-sheet-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="modal-head training-sheet-controls">
          <div className="modal-head-title">
            <Icon name="graduation" size={17} />
            <span id="training-sheet-title">{t("Fiche de formation —")} {sheet?.fullName || '…'}</span>
          </div>
          <button type="button" className="modal-close" onClick={close} disabled={busy} aria-label={t("Fermer")}>
            <Icon name="x" size={18} />
          </button>
        </header>

        <div className="training-sheet-body" ref={bodyRef}>
          {error && <div role="alert" className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}
          {notice && <div role="status" className="alert alert-success">{notice}</div>}
          {loading && <div className="table-empty"><span className="spinner" /></div>}
          {!loading && sheet && (
            <>
              <div className="training-sheet-toolbar training-sheet-controls">
                <span className="training-workflow-status">{status}</span>
                <span>{t("Évaluation et validation hebdomadaires : formatrice")} <strong>{t("ou")}</strong> {t("Team Leader affecté.")}</span>
                {dirty && <strong className="training-unsaved">{t("Modifications non enregistrées")}</strong>}
              </div>
              <div className="training-paper-scroll">
                <article className="training-paper">
                  <header className="training-document-head">
                    <img src="/sage-training-logo.png" alt="SAGE Automotive Interiors" />
                    <h1>{t("Fiche formation Nouveau recrue")}</h1>
                    <div className="training-document-reference">
                      <strong>SAGE-FOR-DRH-34</strong>
                      <span>{t("Révision : 00")}</span>
                      <span>{t("Date : 10/07/2023")}</span>
                    </div>
                  </header>
                  <section className="training-document-identity" aria-label={t("Informations du salarié")}>
                    <div className="training-document-position"><span>{t("POSTE")}</span><strong>{fixedPosition ? t(sheet.position) : sheet.position || '—'}</strong></div>
                    <dl>
                      <div><dt>{t("MATRICULE :")}</dt><dd>{sheet.matricule || '—'}</dd></div>
                      <div><dt>{t("NOM ET PRÉNOM :")}</dt><dd>{sheet.fullName}</dd></div>
                      <div><dt>{t("PROJET / LIGNE :")}</dt><dd>{sheet.department || '—'}</dd></div>
                      <div><dt>{t("DATE DÉBUT :")}</dt><dd>{formatDate(periods[0]?.startDate)}</dd></div>
                      <div><dt>{t("DATE FIN :")}</dt><dd>{formatDate(periods[4]?.endDate)}</dd></div>
                      <div><dt>{t("FORMATRICE :")}</dt><dd>{sheet.trainerName || t("Non affectée")}</dd></div>
                    </dl>
                  </section>

                  <nav className="document-steps training-sheet-controls" aria-label={t("Semaines de formation")}>
                    {periods.map((period, index) => (
                      <button type="button" key={period.id} aria-current={activeWeek === index ? 'step' : undefined}
                        onClick={() => setActiveWeek(index)}>
                        <strong>{t(period.title)}</strong>
                        <small>{isApproved(sheet.periods[index]) ? t("Validée") : t("{0} / {1} notes", { 0: period.competencies.filter((c) => c.rating !== '').length, 1: period.competencies.length })}</small>
                      </button>
                    ))}
                    <button type="button" aria-current={activeWeek === periods.length ? 'step' : undefined}
                      onClick={() => setActiveWeek(periods.length)}><strong>{t("Avis et validations")}</strong><small>{status}</small></button>
                  </nav>
                  <p className="document-hint training-sheet-controls">{t("Sélectionnez une semaine. Vos saisies sont conservées en changeant d’étape ; enregistrez avant de valider ou d’imprimer.")}</p>
                  {periods.map((period, weekIndex) => {
                    const stored = sheet.periods[weekIndex];
                    const ready = !dirty && (weekIndex === 0 || isApproved(sheet.periods[weekIndex - 1]))
                      && stored.startDate && stored.endDate && stored.competencies.every((c) => c.rating != null);
                    const completed = period.competencies.filter((c) => c.rating !== '').length;
                    return (
                      <section key={period.id} className={`document-stage${activeWeek !== weekIndex ? ' document-inactive' : ''}`}>
                        <div className="document-section-heading"><h2>{t(period.title)}</h2><span>{completed} / {period.competencies.length} {t("critères évalués")}</span></div>
                        <progress className="document-progress" value={completed} max={period.competencies.length}
                          aria-label={t("Progression {0}", { 0: t(period.title) })} />
                        <div className="document-fields">
                          <label>{t("Début")} <input className="form-input training-sheet-controls" type="date" aria-label={t("Début {0}", { 0: t(period.title) })} value={period.startDate}
                              disabled={!canEdit || busy} onChange={(event) => patchPeriod(weekIndex, { startDate: event.target.value })} />
                            <span className="document-print-value">{formatDate(period.startDate)}</span>
                          </label>
                          <label>{t("Fin")} <input className="form-input training-sheet-controls" type="date" aria-label={t("Fin {0}", { 0: t(period.title) })} value={period.endDate}
                              disabled={!canEdit || busy} onChange={(event) => patchPeriod(weekIndex, { endDate: event.target.value })} />
                            <span className="document-print-value">{formatDate(period.endDate)}</span>
                          </label>
                        </div>
                        {SECTIONS.map((section) => (
                          <section className="document-group" key={section.code}>
                            <h3>{t(section.label)}</h3>
                            {period.competencies.map((competency, index) => competency.section === section.code && (
                              <div className="document-criterion" key={competency.id}>
                                <label className="document-rating"><span>{t(competency.label)}</span>
                                  <select className="form-input training-sheet-controls" aria-label={`${t(period.title)} — ${t(competency.label)}`}
                                    value={competency.rating} disabled={!canEdit || busy}
                                    onChange={(event) => patchCompetency(weekIndex, index, { rating: event.target.value })}>
                                    <option value="">{t("À évaluer")}</option>
                                    {RATINGS.map((label, i) => <option key={label} value={i + 1}>{i + 1} — {t(label)}</option>)}
                                  </select>
                                  <strong className="document-print-value">{competency.rating ? `${competency.rating} — ${t(RATINGS[Number(competency.rating) - 1])}` : t("Non évalué")}</strong>
                                </label>
                                <label className="document-comment training-sheet-controls">{t("Commentaire")} <textarea className="form-input" rows={2} maxLength={1000} placeholder={t("Observation facultative")}
                                    aria-label={t("Commentaire {0} — {1}", { 0: t(period.title), 1: t(competency.label) })}
                                    value={competency.comment} disabled={!canEdit || busy}
                                    onChange={(event) => patchCompetency(weekIndex, index, { comment: event.target.value })} />
                                </label>
                                {competency.comment && <p className="document-print-value">{competency.comment}</p>}
                              </div>
                            ))}
                          </section>
                        ))}
                        <div className="document-validation">
                          <h3>{t("Validation — Formatrice ou Team Leader")}</h3>
                          {isApproved(stored) ? (
                            <div className="training-week-seal">
                              <strong>{stored.trainerSignedAt ? t("Formatrice") : t("Team Leader")} : {stored.trainerSignedBy || stored.teamLeaderSignedBy || t("Compte supprimé")}</strong>
                              <span>{formatDateTime(stored.trainerSignedAt || stored.teamLeaderSignedAt)}</span>
                            </div>
                          ) : <>
                            <p>{t("En attente. Complétez les dates et toutes les notes, enregistrez, puis validez dans l’ordre des semaines.")}</p>
                            {(isAssignedTrainer || isAssignedLeader) && <button type="button" className="btn btn-primary training-sheet-controls"
                              disabled={busy || !ready} onClick={() => signWeek(stored)}>{t("Valider")} {t(period.title)}</button>}
                          </>}
                          {dirty && isApproved(stored) && <p className="training-sheet-controls">{t("Validation enregistrée ; une correction peut l’annuler après sauvegarde.")}</p>}
                        </div>
                      </section>
                    );
                  })}
                  <section className={`document-stage${activeWeek !== periods.length ? ' document-inactive' : ''}`}>
                    <h2>{t("Avis de transfert et validations finales")}</h2>
                    <div className="document-fields">
                      {SECTIONS.map((section) => <label key={section.key}>{t(section.label)}
                        <textarea className="form-input training-sheet-controls" rows={4}
                          aria-label={t("Avis de transfert — {0}", { 0: t(section.label) })} value={opinions[section.key] || ''}
                          maxLength={1000} placeholder={t("Avis / observations")} disabled={!canEdit || busy}
                          onChange={(event) => setOpinions((current) => ({ ...current, [section.key]: event.target.value }))} />
                        <span className="document-print-value">{opinions[section.key] || '—'}</span>
                      </label>)}
                    </div>

                  <section className="training-document-signatures" aria-label={t("Signatures")}>
                    <div>
                      <h2>{t("Signature Formatrice / Team Leader")}</h2>
                      <p>{t("Validations hebdomadaires nominatives ci-dessus")}</p>
                      <strong>{approvedCount} {t("/ 5 semaines validées")}</strong>
                    </div>
                    <div>
                      <h2>{t("Signature Superviseur")}</h2>
                      {sheet.supervisorSignedAt ? (
                        <><strong>{sheet.supervisorSignedBy || t("Compte supprimé")}</strong><p>{formatDateTime(sheet.supervisorSignedAt)}</p></>
                      ) : <span className="training-pending">{t("En attente des cinq validations hebdomadaires")}</span>}
                      {user.role === 'SUPERVISEUR' && !sheet.supervisorSignedAt && (
                        <button type="button" className="btn btn-secondary training-sheet-controls"
                          disabled={busy || dirty || approvedCount !== 5}
                          onClick={() => runAction(() => recruitmentApi.signTrainingSheetSupervisor(candidateId), 'Fiche validée par le Superviseur.')}> {t("Valider la fiche")} </button>
                      )}
                    </div>
                    <div>
                      <h2>{t("Signature Qualité")}</h2>
                      {sheet.qualitySignedAt ? (
                        <><strong>{sheet.qualitySignedBy || t("Compte supprimé")}</strong><p>{formatDateTime(sheet.qualitySignedAt)}</p></>
                      ) : <span className="training-pending">{t("En attente de la validation du Superviseur")}</span>}
                      {user.role === 'QUALITY' && !sheet.qualitySignedAt && (
                        <button type="button" className="btn btn-secondary training-sheet-controls"
                          disabled={busy || dirty || !sheet.supervisorSignedAt}
                          onClick={() => runAction(() => recruitmentApi.signTrainingSheetQuality(candidateId), 'Fiche validée par Qualité.')}> {t("Valider la conformité")} </button>
                      )}
                    </div>
                  </section>
                  </section>
                  <div className="document-navigation training-sheet-controls">
                    <button type="button" className="btn btn-secondary" disabled={activeWeek === 0} onClick={() => setActiveWeek((week) => week - 1)}>{t("Précédent")}</button>
                    <span>{t("Étape")} {activeWeek + 1} / {periods.length + 1}</span>
                    <button type="button" className="btn btn-secondary" disabled={activeWeek === periods.length} onClick={() => setActiveWeek((week) => week + 1)}>{t("Suivant")}</button>
                  </div>
                </article>
              </div>
            </>
          )}
        </div>
        <footer className="modal-foot training-sheet-controls">
          <button type="button" className="btn btn-secondary" onClick={close} disabled={busy}>{t("Fermer")}</button>
          {sheet && !loading && (
            <button type="button" className="btn btn-secondary" disabled={busy || dirty} onClick={() => window.print()}>
              <Icon name="printer" size={14} />{t("Imprimer / PDF")} </button>
          )}
          {sheet && !loading && canEdit && (
            <button type="button" className="btn btn-primary" disabled={busy || !dirty} onClick={save}>
              {busy ? <span className="btn-spinner" /> : <Icon name="check" size={14} />}{t("Enregistrer la fiche")} </button>
          )}
        </footer>
      </section>
    </div>
  );
}
