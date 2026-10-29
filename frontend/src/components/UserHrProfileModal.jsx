import { useEffect, useRef, useState } from 'react';
import { usersApi } from '../api/users.api';
import { errorMessage } from '../api/client';
import { t, useLocale, getLocale } from '../i18n';

export default function UserHrProfileModal({ user, onClose }) {
  useLocale();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  const dialog = useRef(null);
  useEffect(() => {
    let active = true;
    const previous = document.activeElement;
    dialog.current?.focus();
    usersApi.hrProfile(user.id).then((value) => { if (active) setProfile(value); })
      .catch((err) => { if (active) setError(errorMessage(err)); });
    return () => { active = false; previous?.focus(); };
  }, [user.id]);
  const onKeyDown = (e) => {
    if (e.key === 'Escape') onClose();
    if (e.key === 'Tab') {
      e.preventDefault();
      dialog.current.querySelector('button').focus();
    }
  };
  return <div className="modal-backdrop">
    <section className="modal-card hr-profile-modal" role="dialog" aria-modal="true"
      aria-labelledby="hr-profile-title" ref={dialog} tabIndex={-1} onKeyDown={onKeyDown}>
      <div className="modal-head">
        <h3 id="hr-profile-title">{t('Fiche RH')} — {user.username} · {user.fullName}</h3>
        <button type="button" className="modal-close" onClick={onClose} aria-label={t('Fermer')}>×</button>
      </div>
      <div className="modal-body-pad">
        <p className="muted">{t('Données confidentielles — Super Admin uniquement.')}</p>
        {error && <div role="alert" className="alert alert-error">{error}</div>}
        {!profile && !error && <div role="status"><span className="spinner" /></div>}
        {profile && <>
          <p className="hr-profile-source">{t('Source')} : {profile.sourceFile} · {profile.sheetName}<br />
            {t('Importé le')} : {new Date(profile.importedAt).toLocaleString(getLocale())}</p>
          <dl className="hr-profile-fields">
            {profile.fields.map((field) => <div key={field.column}>
              <dt>{field.column} · {field.label}</dt><dd>{field.value || '—'}</dd>
            </div>)}
          </dl>
        </>}
      </div>
    </section>
  </div>;
}
