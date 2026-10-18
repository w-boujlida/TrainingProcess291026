import { useEffect, useState } from 'react';

function formatNow(d) {
  return d.toLocaleDateString('fr-FR', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
    + ' · ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

export default function PageHeader({ title, subtitle, children }) {
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
          <h2>{title}</h2>
          {subtitle && <span className="header-subtitle">{subtitle}</span>}
        </div>
      </div>
      <div className="header-right">
        {children}
        <span className="datetime">{formatNow(now)}</span>
      </div>
    </div>
  );
}
