import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export default function AdminHomePage() {
  const { t } = useTranslation();

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('admin.title')}</h1>
      <p className="text-slate-600 mb-8">{t('admin.subtitle')}</p>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link
          to="/admin/users"
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm hover:border-brand-mid transition-colors"
        >
          <h2 className="font-semibold text-brand-dark">{t('admin.navUsers')}</h2>
          <p className="text-sm text-slate-600 mt-2">{t('admin.navUsersDesc')}</p>
        </Link>
        <Link
          to="/admin/document-types"
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm hover:border-brand-mid transition-colors"
        >
          <h2 className="font-semibold text-brand-dark">{t('admin.navDocTypes')}</h2>
          <p className="text-sm text-slate-600 mt-2">{t('admin.navDocTypesDesc')}</p>
        </Link>
        <Link
          to="/admin/ocr-queue"
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm hover:border-brand-mid transition-colors sm:col-span-2 lg:col-span-1"
        >
          <h2 className="font-semibold text-brand-dark">{t('admin.navOcrQueue')}</h2>
          <p className="text-sm text-slate-600 mt-2">{t('admin.navOcrQueueDesc')}</p>
        </Link>
        <Link
          to="/admin/audit"
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm hover:border-brand-mid transition-colors"
        >
          <h2 className="font-semibold text-brand-dark">{t('admin.navAuditLog')}</h2>
          <p className="text-sm text-slate-600 mt-2">{t('admin.navAuditLogDesc')}</p>
        </Link>
        <Link
          to="/admin/documents-deleted"
          className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm hover:border-brand-mid transition-colors"
        >
          <h2 className="font-semibold text-brand-dark">{t('admin.navDeletedDocs')}</h2>
          <p className="text-sm text-slate-600 mt-2">{t('admin.navDeletedDocsDesc')}</p>
        </Link>
      </div>
    </div>
  );
}
