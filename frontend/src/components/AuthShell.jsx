import { t, useLocale, getLocale } from '../i18n';
import LanguageSelector from './LanguageSelector';

export default function AuthShell({ children }) {
  useLocale();
  const features = [
    t("Gestion des sessions de formation"),
    t("Suivi des formatrices et team leaders"),
    t("Suivi des formations"),
    t("Tableaux de bord de formation"),
  ];

  return (
    <div className="auth-bg">
      <div className="auth-bg-glow" />
      <div className="auth-bg-grid" />
      <img src="/sage-logo.png" alt="" aria-hidden="true" className="auth-bg-logo" />

      <div className="auth-wrap">
        <div className="auth-left">
          <div className="auth-circle c1" />
          <div className="auth-circle c2" />
          <div className="auth-circle c3" />
          <img src="/sage-logo.png" alt="" aria-hidden="true" className="auth-left-watermark" />

          <div className="auth-left-block">
            <img src="/sage-logo.png" alt="SAGE Automotive Interiors" className="auth-brand-logo" />
          </div>

          <div className="auth-left-block">
            <h1 className="auth-title">Training<br /><span>Process</span><br />System</h1>
            <p className="auth-desc"> {t("Plateforme de gestion et de suivi des formations du personnel de production.")} </p>
          </div>

          <div className="auth-left-block auth-features">
            {features.map((f) => (
              <div key={f} className="auth-feature"><span className="auth-feature-dot" />{t(f)}</div>
            ))}
          </div>
        </div>

        <div className="auth-right">
          <LanguageSelector />
          {children}
          <div className="auth-version">SAGE Training Process — v1.0</div>
        </div>
      </div>
    </div>
  );
}
