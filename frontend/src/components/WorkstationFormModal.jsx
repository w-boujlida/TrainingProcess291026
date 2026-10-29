import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useRef, useState } from 'react';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import Icon from './Icon';
import SignaturePad from './SignaturePad';

const KINDS = { INITIAL: 'Formation initiale', REFRESHER: 'Recyclage' };
const GROUPS = [
  { label: 'Accueil et sécurité', start: 0, end: 8 },
  { label: 'Projet et organisation', start: 8, end: 13 },
  { label: 'Production et qualité', start: 13, end: 21 },
  { label: 'Logistique', start: 21, end: 26 },
];
const DECISIONS = {
  PENDING: 'À compléter', NOT_APPLICABLE: 'Non applicable',
  QUALIFIED: 'Qualifié', NOT_QUALIFIED: 'Non qualifié',
};
const dateLabel = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString(getLocale()) : '—';
const timeLabel = (value) => value ? new Date(value).toLocaleString(getLocale()) : '';

function payload(form) {
  return {
    version: form.version,
    cycles: form.cycles.map((cycle) => ({
      kind: cycle.kind, startDate: cycle.startDate || null, endDate: cycle.endDate || null,
      evaluationDate: cycle.evaluationDate || null, measures: cycle.measures?.trim() || null,
      steps: cycle.steps.map((step) => ({
        id: step.id, deadline: step.deadline || null,
        trainingStatus: step.trainingStatus || null, evaluationStatus: step.evaluationStatus || null,
      })),
    })),
  };
}

export default function WorkstationFormModal({ candidateId, fixedPosition, user, onClose }) {
  useLocale();
  const [form, setForm] = useState(null);
  const [saved, setSaved] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [signingKind, setSigningKind] = useState(null);
  const [activeKind, setActiveKind] = useState('INITIAL');
  const [activeGroup, setActiveGroup] = useState(0);
  const canvasRef = useRef(null);
  const bodyRef = useRef(null);
  const assigned = form && ((user.role === 'FORMATRICE' && form.trainerId === user.id)
    || (user.role === 'TEAM_LEADER' && form.teamLeaderId === user.id));
  const canEdit = ['SUPER_ADMIN', 'ADMIN'].includes(user.role) || assigned;
  const dirty = form && saved && JSON.stringify(payload(form)) !== JSON.stringify(payload(saved));
  const apply = (data) => { setForm(data); setSaved(data); setSigningKind(null); };

  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 });
  }, [activeKind, activeGroup]);

  useEffect(() => {
    let cancelled = false;
    recruitmentApi.getWorkstationForm(candidateId)
      .then((data) => { if (!cancelled) apply(data); })
      .catch((err) => { if (!cancelled) setError(errorMessage(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [candidateId]);
  useEffect(() => {
    const warn = (event) => { if (dirty) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const close = () => {
    if (busy || (dirty && !window.confirm(t("Fermer sans enregistrer les modifications ?")))) return;
    onClose();
  };
  const patchCycle = (kind, patch) => setForm((current) => ({
    ...current, cycles: current.cycles.map((cycle) => cycle.kind === kind ? { ...cycle, ...patch } : cycle),
  }));
  const patchStep = (kind, id, patch) => setForm((current) => ({
    ...current, cycles: current.cycles.map((cycle) => cycle.kind !== kind ? cycle : ({
      ...cycle, steps: cycle.steps.map((step) => step.id === id ? { ...step, ...patch } : step),
    })),
  }));
  const run = async (action, message) => {
    setBusy(true); setError(''); setNotice('');
    try { apply(await action()); setNotice(message); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const save = () => {
    if (saved.cycles.some((cycle) => cycle.operatorSignedAt || cycle.steps.some((step) => step.signedAt))
      && !window.confirm(t("Les modifications annulent les signatures des étapes concernées et l’émargement du cycle modifié. Continuer ?"))) return;
    run(() => recruitmentApi.updateWorkstationForm(candidateId, payload(form)), t("Fiche d’intégration enregistrée."));
  };
  const signOperator = () => {
    const canvas = canvasRef.current;
    const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
    if (!pixels.some((value, index) => index % 4 === 3 && value > 0)) {
      setError(t("Dessinez la signature de l’opérateur avant de confirmer.")); return;
    }
    run(() => recruitmentApi.signWorkstationOperator(candidateId, signingKind, form.version, canvas.toDataURL('image/png')),
      t("Émargement de l’opérateur enregistré."));
  };
  const inputDate = (kind, field, label, value) => (
    <label className="workstation-date-field">
      <span>{t(label)}</span>
      <input type="date" className="workstation-controls" aria-label={`${t(label)} — ${t(KINDS[kind])}`}
        value={value || ''} disabled={!canEdit || busy}
        onChange={(event) => patchCycle(kind, { [field]: event.target.value })} />
      <span className="workstation-print">{dateLabel(value)}</span>
    </label>
  );

  return (
    <div className="modal-backdrop workstation-backdrop" onMouseDown={close}>
      <section className="modal-card workstation-modal document-wizard" role="dialog" aria-modal="true"
        aria-labelledby="workstation-title" onMouseDown={(event) => event.stopPropagation()}>
        <header className="modal-head workstation-controls">
          <div className="modal-head-title"><Icon name="fileSheet" size={17} />
            <span id="workstation-title">{t("Fiche d’intégration —")} {form?.fullName || '…'}</span>
          </div>
          <button className="modal-close" type="button" onClick={close} disabled={busy} aria-label={t("Fermer")}><Icon name="x" /></button>
        </header>
        <div className="workstation-body" ref={bodyRef}>
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {notice && <div className="alert alert-success" role="status">{notice}</div>}
          {loading && <div className="table-empty"><span className="spinner" /></div>}
          {form && !loading && (
            <>
              <p className="workstation-help workstation-controls"> {t("Formation : X = réalisée, NA = non applicable. Évaluation : OK / NOK. Qualification à partir de 80 %.")} {dirty && <strong>{t("Modifications non enregistrées.")}</strong>}
              </p>
              <div className="workstation-scroll">
                <article className="workstation-paper">
                  <header className="training-document-head">
                    <img src="/sage-training-logo.png" alt="SAGE Automotive Interiors" />
                    <h1>{t("Fiche d’intégration et de formation au poste")}</h1>
                    <div className="training-document-reference"><strong>SAGE-FOR-DRH-11</strong><span>{t("Révision : 01")}</span><span>{t("Date : 24/05/2022")}</span></div>
                  </header>
                  <div className="workstation-identity">
                    <span><strong>{t("Nom et prénom de l’opérateur :")}</strong> {form.fullName}</span>
                    <span><strong>{t("Matricule :")}</strong> {form.matricule || '—'}</span>
                    <span><strong>{t("Poste :")}</strong> {fixedPosition ? t(form.position) : form.position || '—'}</span>
                    <span><strong>{t("Projet / ligne :")}</strong> {form.department || '—'}</span>
                  </div>
                  <nav className="document-steps workstation-controls" aria-label={t("Cycles de formation")}>
                    {form.cycles.map((cycle) => <button type="button" key={cycle.kind}
                      aria-current={activeKind === cycle.kind ? 'step' : undefined} disabled={busy || Boolean(signingKind)}
                      onClick={() => { setActiveKind(cycle.kind); setActiveGroup(0); }}>
                      <strong>{t(KINDS[cycle.kind])}</strong><small>{t("Cycle indépendant")}</small>
                    </button>)}
                  </nav>
                  {form.cycles.map((cycle) => {
                    const stored = saved.cycles.find((item) => item.kind === cycle.kind);
                    const completed = cycle.steps.filter((step) => step.trainingStatus === 'NA'
                      || (step.trainingStatus === 'X' && step.evaluationStatus)).length;
                    return <section key={cycle.kind} className={`document-cycle${activeKind !== cycle.kind ? ' document-inactive' : ''}`}>
                      <div className="document-section-heading"><h2>{t(KINDS[cycle.kind])}</h2><span>{completed} / {cycle.steps.length} {t("étapes renseignées")}</span></div>
                      <progress className="document-progress" value={completed} max={cycle.steps.length} aria-label={t("Progression {0}", { 0: KINDS[cycle.kind] })} />
                      <div className="document-fields">
                        {inputDate(cycle.kind, 'startDate', t("Du"), cycle.startDate)}
                        {inputDate(cycle.kind, 'endDate', t("Au"), cycle.endDate)}
                        {inputDate(cycle.kind, 'evaluationDate', t("Évaluation le"), cycle.evaluationDate)}
                      </div>
                      <nav className="document-steps workstation-controls" aria-label={t("Rubriques — {0}", { 0: KINDS[cycle.kind] })}>
                        {GROUPS.map((group, index) => <button type="button" key={group.label}
                          aria-current={activeGroup === index ? 'step' : undefined} onClick={() => setActiveGroup(index)}>
                          <strong>{index + 1}. {t(group.label)}</strong>
                        </button>)}
                        <button type="button" aria-current={activeGroup === GROUPS.length ? 'step' : undefined}
                          onClick={() => setActiveGroup(GROUPS.length)}><strong>{t("5. Bilan et signature")}</strong></button>
                      </nav>
                      {GROUPS.map((group, index) => <section key={group.label}
                        className={`document-stage${activeGroup !== index ? ' document-inactive' : ''}`}>
                        <h3>{t(group.label)}</h3>
                        {cycle.steps.slice(group.start, group.end).map((step) => (
                          <article className="document-step-card" key={step.id}>
                            <h4>{t(step.label)}</h4>
                            <p className="document-hint">{t("Postes concernés :")} {t(step.concernedPositions)} · {step.applicability}</p>
                            <div className="document-fields">
                              <label>{t("Délai")} <input type="date" className="form-input workstation-controls" aria-label={t("Délai {0} — {1}", { 0: cycle.kind, 1: t(step.label) })}
                                  value={step.deadline || ''} disabled={!canEdit || busy}
                                  onChange={(event) => patchStep(cycle.kind, step.id, { deadline: event.target.value })} />
                                <span className="document-print-value">{dateLabel(step.deadline)}</span>
                              </label>
                              <label>{t("Formation")} <select className="form-input workstation-controls" aria-label={t("Formation {0} — {1}", { 0: cycle.kind, 1: t(step.label) })}
                                  value={step.trainingStatus || ''} disabled={!canEdit || busy}
                                  onChange={(event) => patchStep(cycle.kind, step.id, {
                                    trainingStatus: event.target.value, evaluationStatus: event.target.value === 'X' ? step.evaluationStatus : null,
                                  })}>
                                  <option value="">{t("À renseigner")}</option><option value="X">{t("X — Réalisée")}</option><option value="NA">{t("NA — Non applicable")}</option>
                                </select>
                                <span className="document-print-value">{step.trainingStatus || '—'}</span>
                              </label>
                              <label>{t("Évaluation")} <select className="form-input workstation-controls" aria-label={t("Évaluation {0} — {1}", { 0: cycle.kind, 1: t(step.label) })}
                                  value={step.evaluationStatus || ''} disabled={!canEdit || busy || step.trainingStatus !== 'X'}
                                  onChange={(event) => patchStep(cycle.kind, step.id, { evaluationStatus: event.target.value })}>
                                  <option value="">{t("À évaluer")}</option><option value="OK">{t("OK — Satisfaisant")}</option><option value="NOK">{t("NOK — Non satisfaisant")}</option>
                                </select>
                                <span className="document-print-value">{step.evaluationStatus || '—'}</span>
                              </label>
                            </div>
                            <div className="document-step-signature">
                              {step.signedAt ? <span>{t("Signé par")} <strong>{step.signedBy}</strong> · {timeLabel(step.signedAt)}</span> : <span>{t("Étape non signée")}</span>}
                              {assigned && !step.signedAt && <button type="button" className="btn btn-secondary workstation-controls"
                                disabled={busy || dirty || step.trainingStatus !== 'X' || !cycle.startDate || !cycle.endDate}
                                onClick={() => run(() => recruitmentApi.signWorkstationStep(candidateId, cycle.kind, step.id, form.version), t('Étape signée.'))}
                                aria-label={t("Signer {0} — {1}", { 0: cycle.kind, 1: t(step.label) })}>{t("Signer l’étape")}</button>}
                            </div>
                          </article>
                        ))}
                      </section>)}
                      <section className={`document-stage${activeGroup !== GROUPS.length ? ' document-inactive' : ''}`}>
                        <h3>{t("Bilan et signature —")} {t(KINDS[cycle.kind])}</h3>
                        <div className="document-result">
                          <strong>{t("Note :")} {stored.score == null ? '—' : `${stored.score.toLocaleString(getLocale())} %`}</strong>
                          <span>{stored.successful} {t("OK /")} {stored.evaluated} {t("évaluées · seuil de qualification : 80 %")}</span>
                          <strong className={`workstation-decision decision-${stored.decision}`}>{t(DECISIONS[stored.decision])}</strong>
                          {dirty && <p className="workstation-controls">{t("Résultat enregistré. Enregistrez vos modifications pour actualiser le bilan.")}</p>}
                        </div>
                        <label className="document-comment">{t("Mesures à prendre")} <textarea className="form-input workstation-controls" rows={4} value={cycle.measures || ''} maxLength={3000}
                            disabled={!canEdit || busy} aria-label={t("Mesures à prendre — {0}", { 0: KINDS[cycle.kind] })}
                            onChange={(event) => patchCycle(cycle.kind, { measures: event.target.value })} />
                          <span className="document-print-value">{cycle.measures || '—'}</span>
                        </label>
                        <div className="document-validation">
                          <h4>{t("Émargement de l’opérateur")}</h4>
                          {cycle.operatorSignature ? <>
                            <img className="workstation-operator-signature" src={cycle.operatorSignature} alt={t("Signature de l’opérateur — {0}", { 0: KINDS[cycle.kind] })} />
                            <p>{timeLabel(cycle.operatorSignedAt)} {t("· recueillie par")} {cycle.operatorRecordedBy}</p>
                          </> : <p>{t("Non signé. Complétez les dates et les évaluations, faites signer toutes les étapes X puis enregistrez avant de recueillir l’émargement.")}</p>}
                          {canEdit && !cycle.operatorSignature && <button type="button" className="btn btn-primary workstation-controls"
                            disabled={busy || dirty || cycle.steps.some((step) => step.trainingStatus !== 'NA'
                              && (step.trainingStatus !== 'X' || !step.evaluationStatus || !step.signedAt))
                              || !cycle.evaluationDate || !cycle.startDate || !cycle.endDate || !cycle.steps.some((step) => step.trainingStatus === 'X')}
                            onClick={() => { setError(''); setSigningKind(cycle.kind); }}> {t("Faire signer l’opérateur —")} {t(KINDS[cycle.kind])}
                          </button>}
                        </div>
                      </section>
                      <div className="document-navigation workstation-controls">
                        <button type="button" className="btn btn-secondary" disabled={activeGroup === 0} onClick={() => setActiveGroup((index) => index - 1)}>{t("Précédent")}</button>
                        <span>{t("Rubrique")} {activeGroup + 1} / {GROUPS.length + 1}</span>
                        <button type="button" className="btn btn-secondary" disabled={activeGroup === GROUPS.length} onClick={() => setActiveGroup((index) => index + 1)}>{t("Suivant")}</button>
                      </div>
                    </section>;
                  })}
                  <p className="workstation-legend">{t("I : initiale · R : recyclage · E : évaluation. X : formation réalisée · NA : non applicable (exclue de la note). OK : satisfaisant · NOK : non satisfaisant. Une décision définitive exige l’évaluation de toutes les étapes applicables.")} </p>
                </article>
              </div>
              {signingKind && <section className="workstation-sign-editor workstation-controls">
                <h3>{t("Émargement de")} {form.fullName} — {t(KINDS[signingKind])}</h3>
                <p>{t("L’opérateur dessine sa signature ci-dessous ; votre compte enregistre son recueil.")}</p>
                <SignaturePad canvasRef={canvasRef} disabled={busy} onClear={() => {
                  const canvas = canvasRef.current; canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
                }} />
                <button type="button" className="btn btn-primary" disabled={busy || dirty} onClick={signOperator}>{t("Confirmer l’émargement")}</button>
                <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => setSigningKind(null)}>{t("Annuler")}</button>
              </section>}
            </>
          )}
        </div>
        <footer className="modal-foot workstation-controls">
          <button type="button" className="btn btn-secondary" onClick={close} disabled={busy}>{t("Fermer")}</button>
          {form && <button type="button" className="btn btn-secondary" disabled={busy || dirty} onClick={() => window.print()}>
            <Icon name="printer" />{t("Imprimer / PDF")} </button>}
          {form && canEdit && <button type="button" className="btn btn-primary" disabled={busy || !dirty} onClick={save}>
            {busy ? <span className="btn-spinner" /> : <Icon name="check" />}{t("Enregistrer la fiche")} </button>}
        </footer>
      </section>
    </div>
  );
}
