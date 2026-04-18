import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

/**
 * @param {React.ReactNode} children
 * @param {string[]} roles — au moins un rôle requis
 */
export default function RoleGate({ children, roles }) {
  const { t } = useTranslation();
  const role = useEffectiveRole();

  if (!role || !roles.includes(role)) {
    return (
      <div className="p-8 max-w-lg mx-auto">
        <h1 className="text-xl font-semibold text-brand-dark mb-2">{t('admin.forbiddenTitle')}</h1>
        <p className="text-slate-600 mb-4">{t('roleGate.forbiddenBody')}</p>
        <Link className="text-brand-mid hover:underline" to="/dashboard">
          {t('admin.backDashboard')}
        </Link>
      </div>
    );
  }

  return children;
}
