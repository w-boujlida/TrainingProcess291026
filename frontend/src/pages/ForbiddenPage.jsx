import { Link } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import Icon from '../components/Icon';

export default function ForbiddenPage() {
  return (
    <>
      <PageHeader title="Accès refusé" />
      <div className="content">
        <div className="empty-state">
          <div className="empty-icon"><Icon name="lock" size={28} /></div>
          <h3>403 — Accès refusé</h3>
          <p>Vous n'avez pas les droits nécessaires pour accéder à cette page.</p>
          <Link to="/" className="btn btn-primary">Retour à l'accueil</Link>
        </div>
      </div>
    </>
  );
}
