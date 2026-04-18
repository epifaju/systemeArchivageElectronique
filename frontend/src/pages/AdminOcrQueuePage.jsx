import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getOcrQueueStats, listOcrQueue } from '../api/adminApi';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

function formatInstant(value, locale) {
  if (!value) return '—';
  try {
    const d = typeof value === 'string' ? new Date(value) : new Date(value);
    return d.toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' });
  } catch {
    return String(value);
  }
}

export default function AdminOcrQueuePage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';
  const role = useEffectiveRole();
  const backTo = role === 'ADMIN' ? '/admin' : '/dashboard';
  const backLabel = role === 'ADMIN' ? t('admin.backHub') : t('nav.dashboard');

  const [page, setPage] = useState(0);

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['admin-ocr-queue-stats'],
    queryFn: getOcrQueueStats,
    refetchInterval: 15_000,
  });

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-ocr-queue', page],
    queryFn: () => listOcrQueue(page, 20),
    refetchInterval: 15_000,
  });

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <Link className="text-sm text-brand-mid hover:underline" to={backTo}>
        ← {backLabel}
      </Link>
      <h1 className="text-2xl font-semibold text-brand-dark mt-2 mb-2">{t('ocrQueue.title')}</h1>
      <p className="text-slate-600 mb-6">{t('ocrQueue.subtitle')}</p>

      <div className="grid gap-4 sm:grid-cols-3 mb-8">
        <StatCard
          label={t('ocrQueue.stat.pending')}
          value={statsLoading ? '…' : stats?.pending ?? '—'}
          tone="amber"
        />
        <StatCard
          label={t('ocrQueue.stat.processing')}
          value={statsLoading ? '…' : stats?.processing ?? '—'}
          tone="blue"
        />
        <StatCard
          label={t('ocrQueue.stat.failed')}
          value={statsLoading ? '…' : stats?.failed ?? '—'}
          tone="red"
        />
      </div>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('admin.loadError')}
        </p>
      )}

      <h2 className="font-semibold text-slate-800 mb-2">{t('ocrQueue.jobsTitle')}</h2>
      {isLoading ? (
        <p className="text-slate-600">{t('search.loading')}</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left">
                <tr>
                  <th className="p-2">ID</th>
                  <th className="p-2">{t('ocrQueue.col.document')}</th>
                  <th className="p-2">{t('ocrQueue.col.status')}</th>
                  <th className="p-2">{t('ocrQueue.col.started')}</th>
                  <th className="p-2">{t('ocrQueue.col.completed')}</th>
                  <th className="p-2">{t('ocrQueue.col.retries')}</th>
                  <th className="p-2 min-w-[180px]">{t('ocrQueue.col.error')}</th>
                </tr>
              </thead>
              <tbody>
                {content.map((job) => (
                  <tr key={job.id} className="border-t border-slate-100">
                    <td className="p-2 font-mono text-xs">{job.id}</td>
                    <td className="p-2">
                      <Link
                        className="text-brand-mid hover:underline"
                        to={`/documents/${job.documentId}`}
                      >
                        #{job.documentId}
                      </Link>
                    </td>
                    <td className="p-2">
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-xs">
                        {t(`ocrQueue.jobStatus.${job.status}`)}
                      </span>
                    </td>
                    <td className="p-2 text-slate-600 whitespace-nowrap">
                      {formatInstant(job.startedAt, locale)}
                    </td>
                    <td className="p-2 text-slate-600 whitespace-nowrap">
                      {formatInstant(job.completedAt, locale)}
                    </td>
                    <td className="p-2">{job.retryCount ?? 0}</td>
                    <td className="p-2 text-xs text-slate-600 max-w-xs truncate" title={job.errorMessage || ''}>
                      {job.errorMessage || '—'}
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={7} className="p-6 text-center text-slate-500">
                      {t('ocrQueue.empty')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center gap-2">
              <button
                type="button"
                disabled={page <= 0}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                {t('search.prev')}
              </button>
              <span className="text-sm text-slate-600">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
                onClick={() => setPage((p) => p + 1)}
              >
                {t('search.next')}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatCard({ label, value, tone }) {
  const border =
    tone === 'amber'
      ? 'border-amber-200 bg-amber-50'
      : tone === 'blue'
        ? 'border-blue-200 bg-blue-50'
        : 'border-red-200 bg-red-50';
  const text =
    tone === 'amber'
      ? 'text-amber-950'
      : tone === 'blue'
        ? 'text-blue-950'
        : 'text-red-950';

  return (
    <div className={`rounded-lg border p-4 ${border}`}>
      <p className={`text-sm font-medium ${text}`}>{label}</p>
      <p className={`text-3xl font-semibold mt-1 ${text}`}>{value}</p>
    </div>
  );
}
