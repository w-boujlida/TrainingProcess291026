import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '../api/client';
import { recruitmentApi } from '../api/recruitment.api';
import Icon from './Icon';
import SignaturePad from './SignaturePad';

const ASSESSMENTS = [
  { value: 'PENDING', fr: 'À évaluer', ar: 'قيد التقييم' },
  { value: 'ACQUIRED', fr: 'Acquis', ar: 'مكتسب' },
  { value: 'TO_REINFORCE', fr: 'À renforcer', ar: 'يحتاج إلى تعزيز' },
];
const STEPS = ['Salarié et dates', 'Objectifs', 'Évaluation finale', 'Signatures'];
const assessmentLabel = (value) => {
  const assessment = ASSESSMENTS.find((item) => item.value === value);
  return assessment ? t(assessment.fr) : '—';
};

function dateLabel(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString(getLocale()) : '—';
}

function timeLabel(value) {
  return value ? new Date(value).toLocaleString(getLocale()) : '—';
}

function requestData(booklet) {
  return {
    trainingStartDate: booklet.trainingStartDate || null,
    trainingEndDate: booklet.trainingEndDate || null,
    evaluationOutcome: booklet.evaluationOutcome || null,
    evaluationNotes: booklet.evaluationNotes?.trim() || null,
    objectives: booklet.objectives.map((objective) => ({
      key: objective.key,
      assessment: objective.assessment,
      comment: objective.comment?.trim() || null,
    })),
  };
}

function withSnapshot(booklet) {
  return {
    ...booklet,
    _savedStartDate: booklet.trainingStartDate,
    _savedEndDate: booklet.trainingEndDate,
    _savedEvaluationOutcome: booklet.evaluationOutcome,
    _savedEvaluationNotes: booklet.evaluationNotes,
    _savedObjectives: booklet.objectives.map(({ key, assessment, comment }) => ({
      key, assessment, comment: comment?.trim() || null,
    })),
  };
}

export default function IntegrationBookletModal({ candidateId, fixedPosition, canEdit, user, onClose }) {
  useLocale();
  const [booklet, setBooklet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [activeStep, setActiveStep] = useState(0);
  const canvasRef = useRef(null);
  const bodyRef = useRef(null);
  const canSignTrainer = user.role === 'FORMATRICE';
  const stored = booklet && {
    trainingStartDate: booklet._savedStartDate || null,
    trainingEndDate: booklet._savedEndDate || null,
    evaluationOutcome: booklet._savedEvaluationOutcome || null,
    evaluationNotes: booklet._savedEvaluationNotes?.trim() || null,
    objectives: booklet._savedObjectives || [],
  };
  const dirty = Boolean(booklet && JSON.stringify(requestData(booklet)) !== JSON.stringify(stored));

  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 });
  }, [activeStep]);

  useEffect(() => {
    let cancelled = false;
    recruitmentApi.getBooklet(candidateId)
      .then((result) => { if (!cancelled) setBooklet(withSnapshot(result)); })
      .catch((err) => { if (!cancelled) setError(errorMessage(err, t("Impossible de charger le livret."))); })
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

  const update = (key, value) => setBooklet((current) => ({ ...current, [key]: value }));
  const updateObjective = (key, field, value) => setBooklet((current) => ({
    ...current,
    objectives: current.objectives.map((objective) => objective.key === key
      ? { ...objective, [field]: value } : objective),
  }));

  const save = async () => {
    if (!canEdit || !booklet) return booklet;
    const data = requestData(booklet);
    const changed = JSON.stringify(data) !== JSON.stringify(stored);
    const hadSignature = Boolean(booklet.candidateSignedAt || booklet.trainerSignedAt);
    if (changed && hadSignature
      && !window.confirm(t("Modifier le livret annulera les signatures existantes. Le candidat et la formatrice devront signer à nouveau. Continuer ?"))) {
      return null;
    }
    setBusy(true);
    setError('');
    try {
      const saved = await recruitmentApi.updateBooklet(candidateId, data);
      const next = withSnapshot(saved);
      setBooklet(next);
      setNotice(changed && hadSignature
        ? t("Modifications enregistrées. Les signatures ont été annulées ; veuillez signer à nouveau.")
        : t("Livret enregistré."));
      return next;
    } catch (err) {
      setError(errorMessage(err, t("Impossible d’enregistrer le livret.")));
      return null;
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async (event) => {
    event.preventDefault();
    await save();
  };

  const handleCandidateSign = async () => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    if (!pixels.some((value, index) => index % 4 === 3 && value !== 0)) {
      setError(t("Dessinez la signature du candidat avant de confirmer."));
      return;
    }
    const saved = await save();
    if (!saved) return;
    setBusy(true);
    setError('');
    try {
      const signed = await recruitmentApi.signCandidateBooklet(candidateId, canvas.toDataURL('image/png'));
      setBooklet(withSnapshot(signed));
      setNotice(t("Signature du candidat enregistrée."));
    } catch (err) {
      setError(errorMessage(err, t("Impossible d’enregistrer la signature du candidat.")));
    } finally {
      setBusy(false);
    }
  };

  const handleTrainerSign = async () => {
    const saved = await save();
    if (!saved) return;
    setBusy(true);
    setError('');
    try {
      setBooklet(withSnapshot(await recruitmentApi.signTrainerBooklet(candidateId)));
      setNotice(t("Signature numérique de la formatrice enregistrée."));
    } catch (err) {
      setError(errorMessage(err, t("Impossible d’enregistrer la signature de la formatrice.")));
    } finally {
      setBusy(false);
    }
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    canvas?.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
  };

  return (
    <div className="modal-backdrop booklet-backdrop" onMouseDown={close}>
      <form className="modal-card booklet-modal document-wizard" role="dialog" aria-modal="true" aria-labelledby="booklet-dialog-title"
        onSubmit={handleSave} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head booklet-controls">
          <div className="modal-head-title" id="booklet-dialog-title"><Icon name="book" size={17} />{t("Livret d’intégration ·")} {booklet?.fullName || ''}</div>
          <div className="booklet-head-actions">
            {booklet && <span className={`booklet-status status-${booklet.status.toLowerCase()}`}>{booklet.status === 'COMPLETED' ? t("Signé") : booklet.status === 'CANDIDATE_SIGNED' ? t("En attente formatrice") : t("À compléter")}</span>}
            <button type="button" className="tool-btn" onClick={() => window.print()} disabled={!booklet || busy || dirty}><Icon name="fileSheet" size={14} />{t("Imprimer / PDF")}</button>
            <button type="button" className="modal-close" onClick={close} disabled={busy} aria-label={t("Fermer")}><Icon name="x" size={18} /></button>
          </div>
        </div>

        <div className="modal-body-pad booklet-scroll" ref={bodyRef}>
          {error && <div className="alert alert-error booklet-controls" role="alert"><Icon name="alert" size={14} />{error}</div>}
          {notice && <div className="alert alert-success booklet-controls" role="status"><Icon name="checkCircle" size={14} />{notice}</div>}
          {loading && <div className="table-empty"><span className="spinner" /></div>}
          {!loading && booklet && (
            <div className="booklet-paper">
              <header className="booklet-title">
                <span className="booklet-kicker">{t("SAGE TUNISIA · RESSOURCES HUMAINES")}</span>
                <h1>{t("Livret d’intégration")} </h1>
                <p>{t("Plan de formation et suivi des compétences ·")} </p>
              </header>

              <nav className="document-steps booklet-controls" aria-label={t("Sections du livret")}>
                {STEPS.map((label, index) => <button type="button" key={label}
                  aria-current={activeStep === index ? 'step' : undefined} onClick={() => setActiveStep(index)}>
                  <strong>{index + 1}. {t(label)}</strong>
                </button>)}
              </nav>
              <div className="document-section-heading booklet-controls">
                <span>{booklet.objectives.filter((objective) => objective.assessment !== 'PENDING').length} / {booklet.objectives.length} {t("objectifs évalués")}</span>
                {dirty && <strong className="training-unsaved">{t("Modifications non enregistrées")}</strong>}
              </div>
              <p className="document-hint booklet-controls">{t("Les sections restent accessibles librement. Enregistrez avant d’imprimer ; les signatures suivent l’ordre candidat puis formatrice.")}</p>
              <section className={`booklet-section${activeStep !== 0 ? ' document-inactive' : ''}`}>
                <h2>{t("Informations du salarié")} </h2>
                <div className="booklet-identity">
                  <div><small>{t("Nom et prénom")}</small><strong>{booklet.fullName}</strong></div>
                  <div><small>{t("Matricule")}</small><strong>{booklet.matricule || '—'}</strong></div>
                  <div><small>{t("CIN")}</small><strong>{booklet.cin}</strong></div>
                  <div><small>{t("Poste")}</small><strong>{fixedPosition ? t(booklet.position) : booklet.position || '—'}</strong></div>
                  <div><small>{t("Projet / ligne")}</small><strong>{booklet.department || '—'}</strong></div>
                  <div><small>{t("Catégorie")}</small><strong>{t(booklet.category)}</strong></div>
                  <div><small>{t("Formatrice")}</small><strong>{booklet.trainerName || t("À affecter")}</strong></div>
                  <div><small>{t("Niveau")}</small><strong>{t(booklet.trainingLevel) || '—'}</strong></div>
                </div>
              </section>

              <section className={`booklet-section${activeStep !== 0 ? ' document-inactive' : ''}`}>
                <h2>{t("Plan de formation")} </h2>
                <div className="booklet-dates">
                  <label className="form-group"><span className="form-label">{t("Date de début")}</span>
                    <input className="form-input booklet-controls" type="date" value={booklet.trainingStartDate || ''}
                      disabled={!canEdit || busy} onChange={(event) => update('trainingStartDate', event.target.value || null)} />
                    <span className="document-print-value">{dateLabel(booklet.trainingStartDate)}</span>
                  </label>
                  <label className="form-group"><span className="form-label">{t("Date de fin prévue")}</span>
                    <input className="form-input booklet-controls" type="date" value={booklet.trainingEndDate || ''}
                      disabled={!canEdit || busy} onChange={(event) => update('trainingEndDate', event.target.value || null)} />
                    <span className="document-print-value">{dateLabel(booklet.trainingEndDate)}</span>
                  </label>
                </div>
                <p className="booklet-reference-dates">{t("Date de recrutement :")} {dateLabel(booklet.hireDate)}</p>
              </section>

              <section className={`booklet-section${activeStep !== 1 ? ' document-inactive' : ''}`}>
                <h2>{t("Suivi des objectifs")} </h2>
                <div className="booklet-objectives">
                  {booklet.objectives.map((objective, index) => (
                    <article className="booklet-objective" key={objective.key}>
                      <span className="booklet-objective-index">{index + 1}</span>
                      <div className="booklet-objective-copy">
                        <strong>{t(objective.labelFr)}</strong>
                        <input className="form-input booklet-controls" value={objective.comment || ''} maxLength={1000}
                          aria-label={t("Commentaire : {0}", { 0: t(objective.labelFr) })}
                          placeholder={t("Commentaire")} disabled={!canEdit || busy}
                          onChange={(event) => updateObjective(objective.key, 'comment', event.target.value)} />
                        {objective.comment && <p className="document-print-value">{objective.comment}</p>}
                      </div>
                      <select className="form-input booklet-assessment booklet-controls" value={objective.assessment}
                        disabled={!canEdit || busy} aria-label={t("Évaluation : {0}", { 0: t(objective.labelFr) })}
                        onChange={(event) => updateObjective(objective.key, 'assessment', event.target.value)}>
                        {ASSESSMENTS.map((assessment) => (
                          <option value={assessment.value} key={assessment.value}>{t(assessment.fr)}</option>
                        ))}
                      </select>
                      <strong className="document-print-value">{assessmentLabel(objective.assessment)}</strong>
                    </article>
                  ))}
                </div>
              </section>

              <section className={`booklet-section${activeStep !== 2 ? ' document-inactive' : ''}`}>
                <h2>{t("Évaluation finale")} </h2>
                <label className="form-group">
                  <span className="form-label">{t("Résultat")}</span>
                  <select className="form-input booklet-controls" value={booklet.evaluationOutcome || 'PENDING'}
                    disabled={!canEdit || busy} onChange={(event) => update('evaluationOutcome', event.target.value)}>
                    {ASSESSMENTS.map((assessment) => (
                      <option value={assessment.value} key={assessment.value}>{t(assessment.fr)}</option>
                    ))}
                  </select>
                  <span className="document-print-value">{assessmentLabel(booklet.evaluationOutcome || 'PENDING')}</span>
                </label>
                <label className="form-group booklet-notes">
                  <span className="form-label">{t("Observations")}</span>
                  <textarea className="form-input form-textarea booklet-controls" rows="3" maxLength={3000}
                    value={booklet.evaluationNotes || ''} disabled={!canEdit || busy}
                    onChange={(event) => update('evaluationNotes', event.target.value)} />
                  <span className="document-print-value">{booklet.evaluationNotes || '—'}</span>
                </label>
              </section>

              <section className={`booklet-section booklet-signatures${activeStep !== 3 ? ' document-inactive' : ''}`}>
                <h2>{t("Signatures")} </h2>
                <div className="booklet-signature-grid">
                  <div className="booklet-signature">
                    <h3>{t("Signature du candidat")} </h3>
                    {booklet.candidateSignature
                      ? <img className="booklet-signature-image" src={booklet.candidateSignature} alt={t("Signature dessinée du candidat")} />
                      : canEdit && <div className="booklet-controls"><SignaturePad canvasRef={canvasRef} onClear={clearCanvas} disabled={busy} /></div>}
                    <p>{booklet.candidateSignedAt
                      ? t("Signé le {0} · recueilli par {1}", { 0: timeLabel(booklet.candidateSignedAt), 1: booklet.candidateSignedBy })
                      : t("Complétez les dates, les six objectifs et l’évaluation finale, puis faites dessiner la signature du candidat sur cet appareil.")}</p>
                  </div>
                  <div className="booklet-signature">
                    <h3>{t("Signature de la formatrice")} </h3>
                    {booklet.trainerSignedAt
                      ? <div className="booklet-electronic-seal"><Icon name="checkCircle" size={20} /><strong>{t("Signé électroniquement")}</strong><span>{booklet.trainerSignedBy}</span><small>{timeLabel(booklet.trainerSignedAt)}</small></div>
                      : <div className="booklet-signature-empty">{t("Signature depuis le compte de la formatrice affectée.")}</div>}
                    <p>{booklet.trainerSignedAt ? t("Formatrice : {0}", { 0: booklet.trainerSignedBy }) : t("Formatrice affectée : {0}", { 0: booklet.trainerName || t("Non renseigné") })}</p>
                  </div>
                </div>
              </section>
              <div className="document-navigation booklet-controls">
                <button type="button" className="btn btn-secondary" disabled={activeStep === 0} onClick={() => setActiveStep((step) => step - 1)}>{t("Précédent")}</button>
                <span>{t("Étape")} {activeStep + 1} / {STEPS.length}</span>
                <button type="button" className="btn btn-secondary" disabled={activeStep === STEPS.length - 1} onClick={() => setActiveStep((step) => step + 1)}>{t("Suivant")}</button>
              </div>
              <footer className="booklet-footer">{t("Document généré par Training Process · Livret individuel du salarié")}</footer>
            </div>
          )}
        </div>

        {!loading && booklet && (
          <div className="modal-foot booklet-controls">
            <button type="button" className="btn btn-secondary" onClick={close} disabled={busy}>{t("Fermer")}</button>
            {canEdit && <button type="submit" className="btn btn-primary" disabled={busy || !dirty}>
              {busy ? <span className="btn-spinner" /> : <Icon name="check" size={14} />}{t("Enregistrer")} </button>}
            {activeStep === 3 && canEdit && !booklet.candidateSignedAt && (
              <button type="button" className="btn btn-secondary" onClick={handleCandidateSign} disabled={busy}>
                <Icon name="edit" size={14} />{t("Confirmer la signature du candidat")} </button>
            )}
            {activeStep === 3 && canSignTrainer && booklet.trainerId === user.id && booklet.candidateSignedAt && !booklet.trainerSignedAt && (
              <button type="button" className="btn btn-primary" onClick={handleTrainerSign} disabled={busy}>
                <Icon name="shield" size={14} />{t("Signer en tant que formatrice")} </button>
            )}
          </div>
        )}
      </form>
    </div>
  );
}
