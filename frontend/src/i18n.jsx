import { createContext, useContext, useEffect, useState } from 'react';

export const LANGUAGES = { fr: 'Français', en: 'English', it: 'Italiano', ar: 'العربية' };
const LOCALES = { fr: 'fr-FR', en: 'en-GB', it: 'it-IT', ar: 'ar-TN' };
const saved = localStorage.getItem('tp_language');
let language = Object.hasOwn(LANGUAGES, saved) ? saved : 'fr';
let catalogs = {};
const LanguageContext = createContext(null);
export const getLanguage = () => language;
export const getLocale = () => LOCALES[language];

export function t(source, values = {}) {
  if (source == null) return source;
  const translated = language === 'fr' ? source : catalogs[language]?.[source] ?? source;
  return String(translated).replace(/\{(\w+)\}/g, (match, key) => (
    Object.hasOwn(values, key) ? String(values[key] ?? '') : match
  ));
}

export function LanguageProvider({ children }) {
  const [selected, setSelected] = useState(language);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    fetch('/api/i18n')
      .then(async (response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
      .then((data) => { if (active) { catalogs = data; setReady(true); } })
      .catch(() => { if (active) setError('FR : Traductions indisponibles · EN: Translations unavailable · IT: Traduzioni non disponibili · AR: الترجمات غير متوفرة'); });
    return () => { active = false; };
  }, [attempt]);
  useEffect(() => {
    document.documentElement.lang = selected;
    document.documentElement.dir = selected === 'ar' ? 'rtl' : 'ltr';
    document.title = `Training Process — SAGE`;
  }, [selected]);
  const changeLanguage = (value) => {
    if (!Object.hasOwn(LANGUAGES, value)) return;
    language = value;
    localStorage.setItem('tp_language', value);
    setSelected(value);
  };
  if (error) return <div role="alert" className="alert alert-error">{error}
    <button type="button" onClick={() => setAttempt((value) => value + 1)}>Réessayer / Retry / Riprova / إعادة المحاولة</button>
  </div>;
  if (!ready) return <div className="table-empty" role="status">FR · EN · IT · العربية <span className="spinner" /></div>;
  return <LanguageContext.Provider value={{ language: selected, changeLanguage }}>{children}</LanguageContext.Provider>;
}

export const useLocale = () => useContext(LanguageContext);
