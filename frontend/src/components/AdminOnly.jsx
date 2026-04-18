import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

export default function AdminOnly({ children }) {
  const { t } = useTranslation();
  const role = useEffectiveRole();

  if (role !== 'ADMIN') {
    return (
      <div className="p-8 max-w-lg mx-auto">
        <h1 className="text-xl font-semibold text-brand-dark mb-2">{t('admin.forbiddenTitle')}</h1>
        <p className="text-slate-600 mb-4">{t('admin.forbiddenBody')}</p>
        <Link className="text-brand-mid hover:underline" to="/dashboard">
          {t('admin.backDashboard')}
        </Link>
      </div>
    );
  }

  return children;
}
