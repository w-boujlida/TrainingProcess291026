import { useRef, useState } from 'react';
import { recruitmentApi } from '../api/recruitment.api';
import { errorMessage } from '../api/client';
import Icon from './Icon';

export default function ImportExcelModal({ category, onClose, onImported }) {
  const inputRef = useRef(null);
  const [file, setFile] = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const pick = (f) => {
    setError(''); setResult(null);
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.xlsx')) { setError('Format non supporté : sélectionnez un fichier .xlsx.'); return; }
    if (f.size > 10 * 1024 * 1024) { setError('Fichier trop volumineux (10 Mo maximum).'); return; }
    setFile(f);
  };

  const handleImport = async () => {
    setBusy(true); setError('');
    try {
      const res = await recruitmentApi.importExcel(category.code, file);
      setResult(res);
      if (res.created || res.updated) onImported(res);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const downloadTemplate = async () => {
    setError('');
    try { await recruitmentApi.template(category.code); } catch (err) { setError(errorMessage(err)); }
  };

  return (
    <div className="modal-backdrop" onMouseDown={busy ? undefined : onClose}>
      <div className="modal-card" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div className="modal-head-title"><Icon name="upload" size={17} />Import Excel — {category.label}</div>
          <button type="button" className="modal-close" onClick={onClose} disabled={busy} aria-label="Fermer"><Icon name="x" size={18} /></button>
        </div>

        <div className="modal-body-pad modal-scroll">
          {error && <div className="alert alert-error"><Icon name="alert" size={14} />{error}</div>}

          {!result && (
            <>
              <div className="import-steps">
                <div className="import-step">
                  <span className="import-step-num">1</span>
                  <div>
                    <div className="import-step-title">Télécharger le modèle</div>
                    <div className="import-step-text">Colonnes obligatoires : Nom et prénom, CIN. Un CIN existant dans {category.label} met à jour la fiche.</div>
                  </div>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={downloadTemplate}>
                    <Icon name="fileSheet" size={14} />Modèle .xlsx
                  </button>
                </div>
                <div className="import-step">
                  <span className="import-step-num">2</span>
                  <div>
                    <div className="import-step-title">Déposer le fichier rempli</div>
                    <div className="import-step-text">Les lignes valides sont importées, les autres sont listées avec le motif.</div>
                  </div>
                </div>
              </div>

              <div
                className={`dropzone${dragOver ? ' over' : ''}${file ? ' has-file' : ''}`}
                onClick={() => inputRef.current?.click()}
                onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(e) => { e.preventDefault(); setDragOver(false); pick(e.dataTransfer.files[0]); }}
                role="button" tabIndex={0}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click(); }}
              >
                <Icon name={file ? 'fileSheet' : 'upload'} size={28} />
                {file ? (
                  <>
                    <div className="dropzone-title">{file.name}</div>
                    <div className="dropzone-sub">{(file.size / 1024).toFixed(1)} Ko — cliquer pour changer</div>
                  </>
                ) : (
                  <>
                    <div className="dropzone-title">Glisser-déposer le fichier Excel ici</div>
                    <div className="dropzone-sub">ou cliquer pour parcourir (.xlsx, 10 Mo max.)</div>
                  </>
                )}
                <input ref={inputRef} id="import-file" type="file" hidden
                  accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                  onChange={(e) => { pick(e.target.files[0]); e.target.value = ''; }} />
              </div>
            </>
          )}

          {result && (
            <>
              <div className="import-summary">
                <div className="import-sum sum-green"><span>{result.created}</span>Créés</div>
                <div className="import-sum sum-blue"><span>{result.updated}</span>Mis à jour</div>
                <div className={`import-sum ${result.skipped ? 'sum-red' : 'sum-grey'}`}><span>{result.skipped}</span>En erreur</div>
              </div>
              {result.errors.length > 0 ? (
                <div className="import-errors">
                  <div className="import-errors-title"><Icon name="alert" size={14} />Lignes non importées</div>
                  <table className="data-table compact">
                    <thead><tr><th>Ligne</th><th className="left">Motif</th></tr></thead>
                    <tbody>
                      {result.errors.map((e) => (
                        <tr key={e.row}><td className="mono">{e.row}</td><td className="left wrap">{e.message}</td></tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="alert alert-success"><Icon name="checkCircle" size={14} />Toutes les lignes ont été importées.</div>
              )}
            </>
          )}
        </div>

        <div className="modal-foot">
          {result ? (
            <>
              <button type="button" className="btn btn-secondary" onClick={() => { setResult(null); setFile(null); }}>Nouvel import</button>
              <button type="button" className="btn btn-primary" onClick={onClose}><Icon name="check" strokeWidth={2.5} />Terminer</button>
            </>
          ) : (
            <>
              <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>Annuler</button>
              <button type="button" className="btn btn-primary" onClick={handleImport} disabled={!file || busy}>
                {busy ? <span className="btn-spinner" /> : <Icon name="upload" size={14} strokeWidth={2.5} />}
                Importer
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
