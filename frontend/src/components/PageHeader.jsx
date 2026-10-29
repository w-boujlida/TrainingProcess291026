import { t, useLocale, getLocale } from '../i18n';
import { useEffect, useState } from 'react';

function formatNow(d) {
  return d.toLocaleDateString(getLocale(), { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
    + ' · ' + d.toLocaleTimeString(getLocale(), { hour: '2-digit', minute: '2-digit' });
}

export default function PageHeader({ title, subtitle, children }) {
  useLocale();
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(t);
  }, []);

  return (
    <div className="header">
      <div className="header-left">
        <div className="header-accent-bar" />
        <div className="header-title-group">
          <h2>{t(title)}</h2>
          {subtitle && <span className="header-subtitle">{t(subtitle)}</span>}
        </div>
      </div>
      <div className="header-right">
        {children}
        <span className="datetime">{formatNow(now)}</span>
      </div>
    </div>
  );
}
