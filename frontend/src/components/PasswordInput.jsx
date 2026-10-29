import { t, useLocale, getLocale } from '../i18n';
import { useState } from 'react';
import Icon from './Icon';

export default function PasswordInput({ id, value, onChange, autoComplete, placeholder = '••••••••', autoFocus, hasError }) {
  useLocale();
  const [visible, setVisible] = useState(false);
  return (
    <div className="input-icon-wrap">
      <span className="input-icon"><Icon name="lock" /></span>
      <input
        id={id}
        className={`auth-input with-toggle${hasError ? ' has-error' : ''}`}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        placeholder={placeholder}
        autoFocus={autoFocus}
        required
      />
      <button type="button" className="input-toggle" onClick={() => setVisible((v) => !v)}
        aria-label={visible ? t("Masquer le mot de passe") : t("Afficher le mot de passe")}>
        <Icon name={visible ? 'eyeOff' : 'eye'} />
      </button>
    </div>
  );
}
