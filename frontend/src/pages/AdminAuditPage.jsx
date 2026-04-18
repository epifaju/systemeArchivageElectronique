import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { listAuditLogs } from '../api/adminApi';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

function formatInstant(value, locale) {
  if (!value) return '—';
  try {
    const d = typeof value === 'string' ? new Date(value) : new Date(value);
    return d.toLocaleString(locale, { dateStyle: 'short', timeStyle: 'medium' });
  } catch {
    return String(value);
  }
}

function formatResource(row) {
  const t = row.resourceType;
  const id = row.resourceId;
  if (!t && id == null) return '—';
  if (id != null) return `${t ?? '?'} #${id}`;
  return String(t ?? '—');
}

function formatDetails(details) {
  if (details == null || (typeof details === 'object' && Object.keys(details).length === 0)) {
    return '—';
  }
  try {
    const s = JSON.stringify(details);
    if (s.length <= 160) return s;
    return `${s.slice(0, 157)}…`;
  } catch {
    return '—';
  }
}

const emptyFilters = () => ({
  dateFrom: '',
  dateTo: '',
  action: '',
  userId: '',
  resourceType: '',
});

/** Jour local au format YYYY-MM-DD (aligné sur les champs type="date"). */
function localCalendarDateYmd() {
  const n = new Date();
  const y = n.getFullYear();
  const m = String(n.getMonth() + 1).padStart(2, '0');
  const d = String(n.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export default function AdminAuditPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';
  const browserTimeZone = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    []
  );
  const role = useEffectiveRole();
  const backTo = role === 'ADMIN' ? '/admin' : '/dashboard';
  const backLabel = role === 'ADMIN' ? t('admin.backHub') : t('nav.dashboard');

  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState(emptyFilters);
  const [applied, setApplied] = useState(emptyFilters);

  const apiFilters = useMemo(() => {
    const f = { timeZone: browserTimeZone };
    if (applied.dateFrom) f.dateFrom = applied.dateFrom;
    if (applied.dateTo) f.dateTo = applied.dateTo;
    if (applied.action.trim()) f.action = applied.action.trim();
    if (applied.resourceType.trim()) f.resourceType = applied.resourceType.trim();
    if (applied.userId.trim()) {
      const n = Number.parseInt(applied.userId.trim(), 10);
      if (!Number.isNaN(n)) f.userId = n;
    }
    return f;
  }, [applied, browserTimeZone]);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-audit-logs', page, apiFilters],
    queryFn: () => listAuditLogs(page, 50, apiFilters),
  });

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  function applyFilters(e) {
    e?.preventDefault?.();
    setPage(0);
    setApplied({ ...draft });
  }

  function resetFilters() {
    const z = emptyFilters();
    setDraft(z);
    setApplied(z);
    setPage(0);
  }

  function setTodayRange() {
    const ymd = localCalendarDateYmd();
    setDraft((prev) => ({ ...prev, dateFrom: ymd, dateTo: ymd }));
  }

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <Link className="text-sm text-brand-mid hover:underline" to={backTo}>
        ← {backLabel}
      </Link>
      <h1 className="text-2xl font-semibold text-brand-dark mt-2 mb-2">{t('auditLog.title')}</h1>
      <p className="text-slate-600 mb-4">{t('auditLog.subtitle')}</p>
      <p className="text-xs text-slate-500 mb-3">{t('auditLog.filter.dateHint', { tz: browserTimeZone })}</p>

      <form
        onSubmit={applyFilters}
        className="mb-6 rounded-lg border border-slate-200 bg-slate-50/80 p-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6"
      >
        <label className="flex flex-col gap-1 text-xs">
          <span className="text-slate-600">{t('auditLog.filter.dateFrom')}</span>
          <input
            type="date"
            className="rounded border border-slate-300 px-2 py-1.5 text-sm"
            value={draft.dateFrom}
            onChange={(e) => setDraft((d) => ({ ...d, dateFrom: e.target.value }))}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs">
          <span className="text-slate-600">{t('auditLog.filter.dateTo')}</span>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="date"
              className="rounded border border-slate-300 px-2 py-1.5 text-sm min-w-0 flex-1"
              value={draft.dateTo}
              onChange={(e) => setDraft((d) => ({ ...d, dateTo: e.target.value }))}
            />
            <button
              type="button"
              className="shrink-0 rounded border border-slate-300 bg-white px-2 py-1.5 text-xs"
              onClick={setTodayRange}
            >
              {t('auditLog.filter.today')}
            </button>
          </div>
        </label>
        <label className="flex flex-col gap-1 text-xs sm:col-span-2 lg:col-span-1">
          <span className="text-slate-600">{t('auditLog.filter.action')}</span>
          <input
            type="text"
            className="rounded border border-slate-300 px-2 py-1.5 text-sm"
            placeholder={t('auditLog.filter.actionPlaceholder')}
            value={draft.action}
            onChange={(e) => setDraft((d) => ({ ...d, action: e.target.value }))}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs">
          <span className="text-slate-600">{t('auditLog.filter.userId')}</span>
          <input
            type="text"
            inputMode="numeric"
            className="rounded border border-slate-300 px-2 py-1.5 text-sm font-mono"
            placeholder="—"
            value={draft.userId}
            onChange={(e) => setDraft((d) => ({ ...d, userId: e.target.value }))}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs sm:col-span-2 lg:col-span-1">
          <span className="text-slate-600">{t('auditLog.filter.resourceType')}</span>
          <input
            type="text"
            className="rounded border border-slate-300 px-2 py-1.5 text-sm"
            placeholder={t('auditLog.filter.resourceTypePlaceholder')}
            value={draft.resourceType}
            onChange={(e) => setDraft((d) => ({ ...d, resourceType: e.target.value }))}
          />
        </label>
        <div className="flex flex-wrap items-end gap-2 xl:col-span-1">
          <button
            type="submit"
            className="rounded bg-brand-mid px-3 py-1.5 text-sm text-white hover:opacity-90"
          >
            {t('auditLog.filter.apply')}
          </button>
          <button type="button" className="rounded border px-3 py-1.5 text-sm" onClick={resetFilters}>
            {t('auditLog.filter.reset')}
          </button>
        </div>
      </form>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('admin.loadError')}
        </p>
      )}

      {isLoading ? (
        <p className="text-slate-600">{t('search.loading')}</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left">
                <tr>
                  <th className="p-2">ID</th>
                  <th className="p-2">{t('auditLog.col.user')}</th>
                  <th className="p-2">{t('auditLog.col.action')}</th>
                  <th className="p-2">{t('auditLog.col.resource')}</th>
                  <th className="p-2 min-w-[12rem]">{t('auditLog.col.details')}</th>
                  <th className="p-2">{t('auditLog.col.ip')}</th>
                  <th className="p-2">{t('auditLog.col.when')}</th>
                </tr>
              </thead>
              <tbody>
                {content.map((row) => (
                  <tr key={row.id} className="border-t border-slate-100">
                    <td className="p-2 font-mono text-xs">{row.id}</td>
                    <td className="p-2 text-xs">
                      <span className="font-medium text-slate-800">{row.username || '—'}</span>
                      {row.userId != null && (
                        <span className="ml-1 font-mono text-slate-500">({row.userId})</span>
                      )}
                    </td>
                    <td className="p-2">
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium">
                        {row.action}
                      </span>
                    </td>
                    <td className="p-2 text-xs font-mono text-slate-700">{formatResource(row)}</td>
                    <td
                      className="p-2 text-xs text-slate-600 max-w-xs align-top break-all"
                      title={row.details ? JSON.stringify(row.details) : ''}
                    >
                      {formatDetails(row.details)}
                    </td>
                    <td className="p-2 font-mono text-xs text-slate-600">{row.ipAddress || '—'}</td>
                    <td className="p-2 text-slate-600 whitespace-nowrap">
                      {formatInstant(row.createdAt, locale)}
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={7} className="p-6 text-center text-slate-500">
                      {t('auditLog.empty')}
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
