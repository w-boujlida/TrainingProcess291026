import { LANGUAGES, t, useLocale } from '../i18n';

export default function LanguageSelector() {
  const { language, changeLanguage } = useLocale();
  return <label className="language-selector">
    <span>{t('Langue')}</span>
    <select value={language} onChange={(event) => changeLanguage(event.target.value)} aria-label={t('Langue')}>
      {Object.entries(LANGUAGES).map(([code, label]) => <option key={code} value={code} lang={code}>{code.toUpperCase()} · {label}</option>)}
    </select>
  </label>;
}
