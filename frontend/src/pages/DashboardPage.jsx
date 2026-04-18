import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useEffectiveRole } from '../hooks/useEffectiveRole';
import { fetchHomeDashboard } from '../api/dashboardApi';

const STATUS_KEYS = [
  'PENDING',
  'PROCESSING',
  'OCR_SUCCESS',
  'OCR_PARTIAL',
  'OCR_FAILED',
  'NEEDS_REVIEW',
  'VALIDATED',
  'ARCHIVED',
];

function formatInt(n, locale) {
  try {
    return new Intl.NumberFormat(locale).format(n);
  } catch {
    return String(n);
  }
}

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';
  const role = useEffectiveRole();
  const showAdminLinks = role === 'ADMIN';
  const showAuditLink = role === 'ADMIN' || role === 'AUDITEUR';
  const showOcrQueueLink = role === 'ADMIN' || role === 'ARCHIVISTE';

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['home-dashboard'],
    queryFn: fetchHomeDashboard,
  });

  const byStatus = data?.documentsByStatus ?? {};
  const statusesWithCount = STATUS_KEYS.filter((k) => (byStatus[k] ?? 0) > 0);

  return (
    <div className="p-8 max-w-5xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('nav.dashboard')}</h1>

      {isLoading && <p className="text-slate-600 mb-6">{t('search.loading')}</p>}
      {isError && (
        <p className="text-red-600 text-sm mb-6">
          {error?.response?.data?.message || error?.message || t('admin.loadError')}
        </p>
      )}

      {!isLoading && !isError && data && (
        <>
          <p className="text-lg text-slate-800 mb-6">
            {t('dashboard.welcome', { name: data.welcomeName })}
          </p>

          {role === 'AUDITEUR' && (
            <p className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-6">
              {t('dashboard.auditeurHint')}
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 mb-8">
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                {t('dashboard.totalDocs')}
              </p>
              <p className="mt-2 text-3xl font-semibold text-brand-dark tabular-nums">
                {formatInt(data.totalDocuments, locale)}
              </p>
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                {t('dashboard.last7Days')}
              </p>
              <p className="mt-2 text-3xl font-semibold text-brand-dark tabular-nums">
                {formatInt(data.documentsLast7Days, locale)}
              </p>
            </div>
            {data.ocrQueue && (
              <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:col-span-2 lg:col-span-1">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                  {t('dashboard.ocrQueue')}
                </p>
                <div className="mt-2 flex flex-wrap gap-3 text-sm">
                  <span className="rounded bg-amber-50 px-2 py-1 text-amber-900">
                    {t('dashboard.ocrPending')}: {formatInt(data.ocrQueue.pending, locale)}
                  </span>
                  <span className="rounded bg-sky-50 px-2 py-1 text-sky-900">
                    {t('dashboard.ocrProcessing')}: {formatInt(data.ocrQueue.processing, locale)}
                  </span>
                  <span className="rounded bg-red-50 px-2 py-1 text-red-900">
                    {t('dashboard.ocrFailed')}: {formatInt(data.ocrQueue.failed, locale)}
                  </span>
                </div>
                {showOcrQueueLink && (
                  <Link
                    className="mt-3 inline-block text-sm text-brand-mid hover:underline"
                    to="/admin/ocr-queue"
                  >
                    {t('dashboard.viewOcrQueue')} →
                  </Link>
                )}
              </div>
            )}
          </div>

          {role !== 'AUDITEUR' && (
            <div className="mb-8 rounded-xl border border-slate-200 bg-slate-50/80 p-5">
              <h2 className="text-sm font-semibold text-slate-800 mb-3">{t('dashboard.statusBreakdown')}</h2>
              {statusesWithCount.length === 0 ? (
                <p className="text-sm text-slate-500">{t('dashboard.noStatusData')}</p>
              ) : (
                <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 text-sm">
                  {statusesWithCount.map((k) => (
                    <li
                      key={k}
                      className="flex justify-between gap-2 rounded border border-slate-200 bg-white px-3 py-2"
                    >
                      <span className="text-slate-600">{t(`enums.documentStatus.${k}`)}</span>
                      <span className="font-mono font-medium text-slate-900 tabular-nums">
                        {formatInt(byStatus[k] ?? 0, locale)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div className="mb-8">
            <h2 className="text-sm font-semibold text-slate-800 mb-3">{t('dashboard.quickActions')}</h2>
            <div className="flex flex-wrap gap-3">
              {role !== 'AUDITEUR' && (
                <>
                  <Link
                    className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm text-brand-mid shadow-sm hover:border-brand-mid"
                    to="/upload"
                  >
                    {t('nav.upload')}
                  </Link>
                  <Link
                    className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm text-brand-mid shadow-sm hover:border-brand-mid"
                    to="/search"
                  >
                    {t('nav.search')}
                  </Link>
                  <Link
                    className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm text-brand-mid shadow-sm hover:border-brand-mid"
                    to="/documents"
                  >
                    {t('dashboard.goDocuments')}
                  </Link>
                </>
              )}
            </div>
          </div>
        </>
      )}

      {showAdminLinks && (
        <div className="mt-2 rounded-lg border border-slate-200 bg-slate-50/80 p-4">
          <h2 className="text-sm font-semibold text-slate-800 mb-3">{t('dashboard.adminSection')}</h2>
          <div className="flex flex-wrap gap-4 text-sm">
            <Link className="text-brand-mid hover:underline" to="/admin">
              {t('nav.admin')}
            </Link>
            {showAuditLink && (
              <Link className="text-brand-mid hover:underline" to="/admin/audit">
                {t('nav.auditLog')}
              </Link>
            )}
          </div>
        </div>
      )}
      {!showAdminLinks && showAuditLink && (
        <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50/80 p-4">
          <h2 className="text-sm font-semibold text-slate-800 mb-3">{t('dashboard.auditSection')}</h2>
          <Link className="text-sm text-brand-mid hover:underline" to="/admin/audit">
            {t('nav.auditLog')}
          </Link>
        </div>
      )}
    </div>
  );
}
