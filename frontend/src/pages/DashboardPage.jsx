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

function Section({ title, action, children }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3 mb-4">
        <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function KpiCard({ label, value, tone = 'slate' }) {
  const ring =
    tone === 'red'
      ? 'ring-red-100'
      : tone === 'amber'
        ? 'ring-amber-100'
        : tone === 'blue'
          ? 'ring-sky-100'
          : 'ring-slate-100';
  const bg =
    tone === 'red'
      ? 'bg-red-50'
      : tone === 'amber'
        ? 'bg-amber-50'
        : tone === 'blue'
          ? 'bg-sky-50'
          : 'bg-slate-50';
  const text =
    tone === 'red'
      ? 'text-red-950'
      : tone === 'amber'
        ? 'text-amber-950'
        : tone === 'blue'
          ? 'text-sky-950'
          : 'text-slate-900';

  return (
    <div className={`rounded-xl border border-slate-200 ${bg} p-5 shadow-sm ring-1 ${ring}`}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-600">{label}</p>
      <p className={`mt-2 text-3xl font-semibold tabular-nums ${text}`}>{value}</p>
    </div>
  );
}

function SkeletonLine({ w = 'w-36' }) {
  return <div className={`h-3 ${w} rounded bg-slate-200/70 animate-pulse`} />;
}

function SkeletonBlock({ h = 'h-10' }) {
  return <div className={`${h} rounded bg-slate-200/70 animate-pulse`} />;
}

function ActionCard({ to, title, description, badge }) {
  return (
    <Link
      to={to}
      className="group rounded-xl border border-slate-200 bg-white p-5 shadow-sm hover:border-brand-mid focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2 transition-colors"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold text-slate-900 group-hover:text-brand-dark">{title}</h3>
        {badge}
      </div>
      {description && <p className="mt-2 text-sm text-slate-600">{description}</p>}
      <p className="mt-4 text-sm text-brand-mid">{'→'}</p>
    </Link>
  );
}

function formatInt(n, locale) {
  try {
    return new Intl.NumberFormat(locale).format(n);
  } catch {
    return String(n);
  }
}

function formatDocDate(isoDate, loc) {
  if (!isoDate) return '—';
  try {
    const [y, m, d] = String(isoDate).split('T')[0].split('-').map(Number);
    return new Intl.DateTimeFormat(loc, { dateStyle: 'medium' }).format(new Date(y, m - 1, d));
  } catch {
    return String(isoDate);
  }
}

function formatActivityTime(iso, loc) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat(loc, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));
  } catch {
    return String(iso);
  }
}

function activityLabel(t, action) {
  if (!action) return '—';
  const key = `viewer.historyAction.${action}`;
  const label = t(key);
  return label === key ? action : label;
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
      <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-brand-dark">{t('nav.dashboard')}</h1>
          <p className="text-slate-600 mt-1">{t('dashboard.subtitle')}</p>
        </div>
        {role && (
          <span className="self-start sm:self-auto text-xs font-medium rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-slate-700">
            {t(`admin.roles.${role}`)}
          </span>
        )}
      </div>

      {isError && (
        <div className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
          <div className="font-medium">{t('admin.loadError')}</div>
          <div className="mt-1 text-red-800/90">
            {error?.response?.data?.message || error?.message || t('admin.loadError')}
          </div>
        </div>
      )}

      {isLoading && (
        <>
          <div className="mb-6">
            <SkeletonLine w="w-64" />
            <div className="mt-2">
              <SkeletonLine w="w-80" />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-8">
            <SkeletonBlock h="h-24" />
            <SkeletonBlock h="h-24" />
            <SkeletonBlock h="h-24" />
            <SkeletonBlock h="h-24" />
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-8">
            <SkeletonBlock h="h-36" />
            <SkeletonBlock h="h-36" />
            <SkeletonBlock h="h-36" />
            <SkeletonBlock h="h-36" />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <SkeletonBlock h="h-64" />
            <SkeletonBlock h="h-64" />
          </div>
        </>
      )}

      {!isLoading && !isError && data && (
        <>
          <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-lg text-slate-900">{t('dashboard.welcome', { name: data.welcomeName })}</p>
            {showAuditLink && role === 'AUDITEUR' && (
              <Link className="text-sm text-brand-mid hover:underline" to="/admin/audit">
                {t('nav.auditLog')} →
              </Link>
            )}
          </div>

          {role === 'AUDITEUR' && (
            <p className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-6">
              {t('dashboard.auditeurHint')}
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-8">
            <KpiCard label={t('dashboard.totalDocs')} value={formatInt(data.totalDocuments, locale)} />
            <KpiCard label={t('dashboard.last7Days')} value={formatInt(data.documentsLast7Days, locale)} tone="blue" />
            <KpiCard
              label={t('dashboard.ocrPending')}
              value={formatInt(data.ocrQueue?.pending ?? 0, locale)}
              tone="amber"
            />
            <KpiCard
              label={t('dashboard.ocrFailed')}
              value={formatInt(data.ocrQueue?.failed ?? 0, locale)}
              tone={data.ocrQueue?.failed > 0 ? 'red' : 'slate'}
            />
          </div>

          {showOcrQueueLink && data.ocrQueue?.failed > 0 && (
            <div className="mb-8 rounded-xl border border-red-200 bg-red-50 px-4 py-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div className="text-sm text-red-900">
                  <span className="font-medium">{t('dashboard.ocrAlertTitle')}</span>{' '}
                  <span className="text-red-800/90">
                    {t('dashboard.ocrAlertBody', { count: data.ocrQueue.failed })}
                  </span>
                </div>
                <Link className="text-sm font-medium text-red-900 hover:underline" to="/admin/ocr-queue">
                  {t('dashboard.viewOcrQueue')} →
                </Link>
              </div>
            </div>
          )}

          <div className="mb-8">
            <h2 className="text-sm font-semibold text-slate-800 mb-3">{t('dashboard.quickActions')}</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {role !== 'AUDITEUR' && (
                <>
                  <ActionCard
                    to="/upload"
                    title={t('nav.upload')}
                    description={t('dashboard.action.uploadDesc')}
                  />
                  <ActionCard
                    to="/search"
                    title={t('nav.search')}
                    description={t('dashboard.action.searchDesc')}
                  />
                  <ActionCard
                    to="/documents"
                    title={t('dashboard.goDocuments')}
                    description={t('dashboard.action.documentsDesc')}
                  />
                  {showOcrQueueLink ? (
                    <ActionCard
                      to="/admin/ocr-queue"
                      title={t('dashboard.ocrQueue')}
                      description={t('dashboard.action.ocrQueueDesc')}
                      badge={
                        data.ocrQueue?.pending > 0 ? (
                          <span className="text-xs font-medium rounded-full bg-amber-100 text-amber-950 px-2 py-0.5">
                            {formatInt(data.ocrQueue.pending, locale)}
                          </span>
                        ) : null
                      }
                    />
                  ) : (
                    <ActionCard
                      to="/admin/audit"
                      title={t('nav.auditLog')}
                      description={t('dashboard.action.auditDesc')}
                    />
                  )}
                </>
              )}

              {role === 'AUDITEUR' && (
                <>
                  <ActionCard
                    to="/admin/audit"
                    title={t('nav.auditLog')}
                    description={t('dashboard.action.auditDesc')}
                    badge={
                      <span className="text-xs font-medium rounded-full bg-slate-100 text-slate-800 px-2 py-0.5">
                        {t('dashboard.badge.roleAuditeur')}
                      </span>
                    }
                  />
                </>
              )}

              {showAdminLinks && (
                <ActionCard
                  to="/admin"
                  title={t('nav.admin')}
                  description={t('dashboard.action.adminDesc')}
                  badge={
                    <span className="text-xs font-medium rounded-full bg-slate-100 text-slate-800 px-2 py-0.5">
                      {t('dashboard.badge.admin')}
                    </span>
                  }
                />
              )}
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2 mb-8">
            {role !== 'AUDITEUR' && (
              <Section
                title={t('dashboard.recentDocs')}
                action={
                  <Link className="text-sm text-brand-mid hover:underline" to="/documents">
                    {t('dashboard.viewAll')} →
                  </Link>
                }
              >
                {!data.recentDocuments || data.recentDocuments.length === 0 ? (
                  <div className="text-sm text-slate-600">
                    <p className="text-slate-500">{t('dashboard.recentDocsEmpty')}</p>
                    <div className="mt-3 flex flex-wrap gap-3">
                      <Link className="text-sm text-brand-mid hover:underline" to="/upload">
                        {t('dashboard.emptyCta.upload')} →
                      </Link>
                      <Link className="text-sm text-brand-mid hover:underline" to="/search">
                        {t('dashboard.emptyCta.search')} →
                      </Link>
                    </div>
                  </div>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {data.recentDocuments.map((d) => {
                      const typeLabel = locale.startsWith('pt') ? d.documentTypeLabelPt : d.documentTypeLabelFr;
                      return (
                        <li key={d.id} className="py-3 first:pt-0 last:pb-0">
                          <Link className="font-medium text-brand-mid hover:underline" to={`/documents/${d.id}`}>
                            {d.title}
                          </Link>
                          <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-slate-500">
                            <span>{typeLabel || d.documentTypeCode}</span>
                            <span>{formatDocDate(d.documentDate, locale)}</span>
                            <span>{t(`enums.documentStatus.${d.status}`)}</span>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </Section>
            )}

            <Section
              title={role === 'AUDITEUR' ? t('dashboard.auditSection') : t('dashboard.recentActivity')}
              action={
                showAuditLink ? (
                  <Link className="text-sm text-brand-mid hover:underline" to="/admin/audit">
                    {t('nav.auditLog')} →
                  </Link>
                ) : null
              }
            >
              {role !== 'AUDITEUR' ? (
                !data.recentActivity || data.recentActivity.length === 0 ? (
                  <p className="text-sm text-slate-500">{t('dashboard.recentActivityEmpty')}</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {data.recentActivity.map((a) => (
                      <li key={`${a.documentId}-${a.action}-${a.occurredAt}`} className="py-3 first:pt-0 last:pb-0">
                        <Link className="font-medium text-brand-mid hover:underline" to={`/documents/${a.documentId}`}>
                          {a.documentTitle || `#${a.documentId}`}
                        </Link>
                        <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-slate-500">
                          <span>{activityLabel(t, a.action)}</span>
                          <span>{formatActivityTime(a.occurredAt, locale)}</span>
                        </div>
                      </li>
                    ))}
                  </ul>
                )
              ) : (
                <p className="text-sm text-slate-600">{t('dashboard.auditeurHint')}</p>
              )}
            </Section>
          </div>

          {role !== 'AUDITEUR' && (
            <Section title={t('dashboard.statusBreakdown')}>
              {statusesWithCount.length === 0 ? (
                <p className="text-sm text-slate-500">{t('dashboard.noStatusData')}</p>
              ) : (
                <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 text-sm">
                  {statusesWithCount.map((k) => (
                    <li
                      key={k}
                      className="flex justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2"
                    >
                      <span className="text-slate-600">{t(`enums.documentStatus.${k}`)}</span>
                      <span className="font-mono font-medium text-slate-900 tabular-nums">
                        {formatInt(byStatus[k] ?? 0, locale)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Section>
          )}
        </>
      )}
    </div>
  );
}
