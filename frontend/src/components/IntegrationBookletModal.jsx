import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '../api/client';
import { recruitmentApi } from '../api/recruitment.api';
import Icon from './Icon';

const ASSESSMENTS = [
  { value: 'PENDING', fr: 'À évaluer', ar: 'قيد التقييم' },
  { value: 'ACQUIRED', fr: 'Acquis', ar: 'مكتسب' },
  { value: 'TO_REINFORCE', fr: 'À renforcer', ar: 'يحتاج إلى تعزيز' },
];

function dateLabel(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : '—';
}

function timeLabel(value) {
  return value ? new Date(value).toLocaleString('fr-FR') : '—';
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

function SignaturePad({ onClear, canvasRef, disabled }) {
  const drawing = useRef(false);
  const point = (event) => {
    const canvas = canvasRef.current;
    const bounds = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - bounds.left) * (canvas.width / bounds.width),
      y: (event.clientY - bounds.top) * (canvas.height / bounds.height),
    };
  };
  const start = (event) => {
    if (disabled) return;
    event.preventDefault();
    const canvas = canvasRef.current;
    canvas.setPointerCapture(event.pointerId);
    const ctx = canvas.getContext('2d');
    const p = point(event);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#102d24';
    drawing.current = true;
  };
  const move = (event) => {
    if (!drawing.current) return;
    event.preventDefault();
    const p = point(event);
    const ctx = canvasRef.current.getContext('2d');
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
  };
  const stop = () => { drawing.current = false; };

  return (
    <div className="signature-pad-wrap">
      <canvas ref={canvasRef} className="signature-pad" width="700" height="150"
        onPointerDown={start} onPointerMove={move} onPointerUp={stop} onPointerCancel={stop}
        aria-label="Dessinez ici la signature du candidat" />
      <button type="button" className="tool-btn signature-clear" onClick={onClear} disabled={disabled}>
        Effacer
      </button>
    </div>
  );
}

export default function IntegrationBookletModal({ candidateId, canEdit, user, onClose }) {
  const [booklet, setBooklet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const canvasRef = useRef(null);
  const canSignTrainer = user.role === 'FORMATRICE';

  useEffect(() => {
    let cancelled = false;
    recruitmentApi.getBooklet(candidateId)
      .then((result) => { if (!cancelled) setBooklet(withSnapshot(result)); })
      .catch((err) => { if (!cancelled) setError(errorMessage(err, 'Impossible de charger le livret.')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [candidateId]);

  const update = (key, value) => setBooklet((current) => ({ ...current, [key]: value }));
  const updateObjective = (key, field, value) => setBooklet((current) => ({
    ...current,
    objectives: current.objectives.map((objective) => objective.key === key
      ? { ...objective, [field]: value } : objective),
  }));

  const save = async () => {
    if (!canEdit || !booklet) return booklet;
    const data = requestData(booklet);
    const stored = {
      trainingStartDate: booklet._savedStartDate || null,
      trainingEndDate: booklet._savedEndDate || null,
      evaluationOutcome: booklet._savedEvaluationOutcome || null,
      evaluationNotes: booklet._savedEvaluationNotes?.trim() || null,
      objectives: booklet._savedObjectives || [],
    };
    const changed = JSON.stringify(data) !== JSON.stringify(stored);
    const hadSignature = Boolean(booklet.candidateSignedAt || booklet.trainerSignedAt);
    if (changed && hadSignature
      && !window.confirm('Modifier le livret annulera les signatures existantes. Le candidat et la formatrice devront signer à nouveau. Continuer ?')) {
      return null;
    }
    setBusy(true);
    setError('');
    try {
      const saved = await recruitmentApi.updateBooklet(candidateId, data);
      const next = withSnapshot(saved);
      setBooklet(next);
      setNotice(changed && hadSignature
        ? 'Modifications enregistrées. Les signatures ont été annulées ; veuillez signer à nouveau.'
        : 'Livret enregistré.');
      return next;
    } catch (err) {
      setError(errorMessage(err, 'Impossible d’enregistrer le livret.'));
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
      setError('Dessinez la signature du candidat avant de confirmer.');
      return;
    }
    const saved = await save();
    if (!saved) return;
    setBusy(true);
    setError('');
    try {
      const signed = await recruitmentApi.signCandidateBooklet(candidateId, canvas.toDataURL('image/png'));
      setBooklet(withSnapshot(signed));
      setNotice('Signature du candidat enregistrée.');
    } catch (err) {
      setError(errorMessage(err, 'Impossible d’enregistrer la signature du candidat.'));
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
      setNotice('Signature numérique de la formatrice enregistrée.');
    } catch (err) {
      setError(errorMessage(err, 'Impossible d’enregistrer la signature de la formatrice.'));
    } finally {
      setBusy(false);
    }
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    canvas?.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
  };

  return (
    <div className="modal-backdrop booklet-backdrop" onMouseDown={onClose}>
      <form className="modal-card booklet-modal" onSubmit={handleSave} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head booklet-controls">
          <div className="modal-head-title"><Icon name="book" size={17} />Livret d’intégration · {booklet?.fullName || ''}</div>
          <div className="booklet-head-actions">
            {booklet && <span className={`booklet-status status-${booklet.status.toLowerCase()}`}>{booklet.status === 'COMPLETED' ? 'Signé' : booklet.status === 'CANDIDATE_SIGNED' ? 'En attente formatrice' : 'À compléter'}</span>}
            <button type="button" className="tool-btn" onClick={() => window.print()} disabled={!booklet}><Icon name="fileSheet" size={14} />Imprimer / PDF</button>
            <button type="button" className="modal-close" onClick={onClose} aria-label="Fermer"><Icon name="x" size={18} /></button>
          </div>
        </div>

        <div className="modal-body-pad booklet-scroll">
          {error && <div className="alert alert-error booklet-controls"><Icon name="alert" size={14} />{error}</div>}
          {notice && <div className="alert alert-success booklet-controls"><Icon name="checkCircle" size={14} />{notice}</div>}
          {loading && <div className="table-empty"><span className="spinner" /></div>}
          {!loading && booklet && (
            <div className="booklet-paper">
              <header className="booklet-title">
                <span className="booklet-kicker">SAGE TUNISIA · RESSOURCES HUMAINES</span>
                <h1>Livret d’intégration <span lang="ar" dir="rtl">كتيب الإدماج</span></h1>
                <p>Plan de formation et suivi des compétences · <span lang="ar" dir="rtl">خطة التدريب ومتابعة المهارات</span></p>
              </header>

              <section className="booklet-section">
                <h2>Informations du salarié <span lang="ar" dir="rtl">معلومات الموظف</span></h2>
                <div className="booklet-identity">
                  <div><small>Nom et prénom / الاسم واللقب</small><strong>{booklet.fullName}</strong></div>
                  <div><small>Matricule / الرقم الوظيفي</small><strong>{booklet.matricule || '—'}</strong></div>
                  <div><small>CIN / بطاقة التعريف</small><strong>{booklet.cin}</strong></div>
                  <div><small>Poste / المنصب</small><strong>{booklet.position || '—'}</strong></div>
                  <div><small>Projet / ligne · المشروع / خط الإنتاج</small><strong>{booklet.department || '—'}</strong></div>
                  <div><small>Catégorie / الفئة</small><strong>{booklet.category}</strong></div>
                  <div><small>Formatrice / المدربة</small><strong>{booklet.trainerName || 'À affecter'}</strong></div>
                  <div><small>Niveau / المستوى</small><strong>{booklet.trainingLevel || '—'}</strong></div>
                </div>
              </section>

              <section className="booklet-section">
                <h2>Plan de formation <span lang="ar" dir="rtl">خطة التدريب</span></h2>
                <div className="booklet-dates">
                  <label className="form-group"><span className="form-label">Date de début / تاريخ البداية</span>
                    <input className="form-input" type="date" value={booklet.trainingStartDate || ''}
                      disabled={!canEdit || busy} onChange={(event) => update('trainingStartDate', event.target.value || null)} />
                  </label>
                  <label className="form-group"><span className="form-label">Date de fin prévue / تاريخ النهاية المتوقع</span>
                    <input className="form-input" type="date" value={booklet.trainingEndDate || ''}
                      disabled={!canEdit || busy} onChange={(event) => update('trainingEndDate', event.target.value || null)} />
                  </label>
                </div>
                <p className="booklet-reference-dates">Date de recrutement / تاريخ الانتداب : {dateLabel(booklet.hireDate)}</p>
              </section>

              <section className="booklet-section">
                <h2>Suivi des objectifs <span lang="ar" dir="rtl">متابعة الأهداف</span></h2>
                <div className="booklet-objectives">
                  {booklet.objectives.map((objective, index) => (
                    <article className="booklet-objective" key={objective.key}>
                      <span className="booklet-objective-index">{index + 1}</span>
                      <div className="booklet-objective-copy">
                        <strong>{objective.labelFr}</strong>
                        <span lang="ar" dir="rtl">{objective.labelAr}</span>
                        <input className="form-input" value={objective.comment || ''} maxLength={1000}
                          placeholder="Commentaire / ملاحظة" disabled={!canEdit || busy}
                          onChange={(event) => updateObjective(objective.key, 'comment', event.target.value)} />
                      </div>
                      <select className="form-input booklet-assessment" value={objective.assessment}
                        disabled={!canEdit || busy} aria-label={`Évaluation : ${objective.labelFr}`}
                        onChange={(event) => updateObjective(objective.key, 'assessment', event.target.value)}>
                        {ASSESSMENTS.map((assessment) => (
                          <option value={assessment.value} key={assessment.value}>{assessment.fr} · {assessment.ar}</option>
                        ))}
                      </select>
                    </article>
                  ))}
                </div>
              </section>

              <section className="booklet-section">
                <h2>Évaluation finale <span lang="ar" dir="rtl">التقييم النهائي</span></h2>
                <label className="form-group">
                  <span className="form-label">Résultat / النتيجة</span>
                  <select className="form-input" value={booklet.evaluationOutcome || 'PENDING'}
                    disabled={!canEdit || busy} onChange={(event) => update('evaluationOutcome', event.target.value)}>
                    {ASSESSMENTS.map((assessment) => (
                      <option value={assessment.value} key={assessment.value}>{assessment.fr} · {assessment.ar}</option>
                    ))}
                  </select>
                </label>
                <label className="form-group booklet-notes">
                  <span className="form-label">Observations / ملاحظات</span>
                  <textarea className="form-input form-textarea" rows="3" maxLength={3000}
                    value={booklet.evaluationNotes || ''} disabled={!canEdit || busy}
                    onChange={(event) => update('evaluationNotes', event.target.value)} />
                </label>
              </section>

              <section className="booklet-section booklet-signatures">
                <h2>Signatures <span lang="ar" dir="rtl">التوقيعات</span></h2>
                <div className="booklet-signature-grid">
                  <div className="booklet-signature">
                    <h3>Signature du candidat <span lang="ar" dir="rtl">توقيع المترشح</span></h3>
                    {booklet.candidateSignature
                      ? <img className="booklet-signature-image" src={booklet.candidateSignature} alt="Signature dessinée du candidat" />
                      : canEdit && <SignaturePad canvasRef={canvasRef} onClear={clearCanvas} disabled={busy} />}
                    <p>{booklet.candidateSignedAt
                      ? `Signé le ${timeLabel(booklet.candidateSignedAt)} · recueilli par ${booklet.candidateSignedBy}`
                      : 'Le candidat dessine sa signature sur cet appareil.'}</p>
                  </div>
                  <div className="booklet-signature">
                    <h3>Signature de la formatrice <span lang="ar" dir="rtl">توقيع المدربة</span></h3>
                    {booklet.trainerSignedAt
                      ? <div className="booklet-electronic-seal"><Icon name="checkCircle" size={20} /><strong>Signé électroniquement</strong><span>{booklet.trainerSignedBy}</span><small>{timeLabel(booklet.trainerSignedAt)}</small></div>
                      : <div className="booklet-signature-empty">Signature depuis le compte de la formatrice affectée.</div>}
                    <p>{booklet.trainerSignedAt ? `Formatrice : ${booklet.trainerSignedBy}` : `Formatrice affectée : ${booklet.trainerName || 'non renseignée'}`}</p>
                  </div>
                </div>
              </section>
              <footer className="booklet-footer">Document généré par Training Process · Livret individuel du salarié</footer>
            </div>
          )}
        </div>

        {!loading && booklet && (
          <div className="modal-foot booklet-controls">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Fermer</button>
            {canEdit && <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? <span className="btn-spinner" /> : <Icon name="check" size={14} />}Enregistrer
            </button>}
            {canEdit && !booklet.candidateSignedAt && (
              <button type="button" className="btn btn-secondary" onClick={handleCandidateSign} disabled={busy}>
                <Icon name="edit" size={14} />Confirmer la signature du candidat
              </button>
            )}
            {canSignTrainer && booklet.trainerId === user.id && booklet.candidateSignedAt && !booklet.trainerSignedAt && (
              <button type="button" className="btn btn-primary" onClick={handleTrainerSign} disabled={busy}>
                <Icon name="shield" size={14} />Signer en tant que formatrice
              </button>
            )}
          </div>
        )}
      </form>
    </div>
  );
}
