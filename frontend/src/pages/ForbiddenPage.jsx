import { t, useLocale, getLocale } from '../i18n';
import { Link } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

export default function ForbiddenPage() {
  useLocale();
  return (
    <>
      <PageHeader title={t("Accès refusé")} />
      <div className="content">
        <div className="empty-state">
          <div className="empty-icon"><Icon name="lock" size={28} /></div>
          <h3>{t("403 — Accès refusé")}</h3>
          <p>{t("Vous n'avez pas les droits nécessaires pour accéder à cette page.")}</p>
          <Link to="/" className="btn btn-primary">{t("Retour à l'accueil")}</Link>
        </div>
      </div>
    </>
  );
}
