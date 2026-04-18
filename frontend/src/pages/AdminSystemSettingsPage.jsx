import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { fetchSystemSettings } from '../api/adminApi';

function fmt(v) {
  if (v === null || v === undefined || v === '') return '—';
  if (Array.isArray(v)) return v.length ? v.join(', ') : '—';
  return String(v);
}

function fmtBool(v, t) {
  if (v === null || v === undefined) return '—';
  return v ? t('admin.yes') : t('admin.no');
}

function Row({ label, children }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-1 sm:gap-4 py-2.5 border-b border-slate-100 last:border-0">
      <dt className="text-sm text-slate-600">{label}</dt>
      <dd className="col-span-2 text-sm text-brand-dark break-words">{children}</dd>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold text-brand-dark mb-3">{title}</h2>
      <dl className="rounded-lg border border-slate-200 bg-white px-4 py-1">{children}</dl>
    </section>
  );
}

export default function AdminSystemSettingsPage() {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-system-settings'],
    queryFn: fetchSystemSettings,
  });

  if (isLoading) {
    return (
      <div className="p-6 max-w-4xl mx-auto">
        <p className="text-slate-600">{t('search.loading')}</p>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="p-6 max-w-4xl mx-auto">
        <p className="text-red-600">{t('admin.loadError')}</p>
        <Link className="text-sm text-brand-mid mt-4 inline-block" to="/admin">
          {t('admin.backHub')}
        </Link>
      </div>
    );
  }

  const jwt = data.jwt || {};
  const ocr = data.ocr || {};
  const storage = data.storage || {};
  const clamav = data.clamav || {};
  const rate = data.authRateLimit || {};
  const ingest = data.ingestWatch || {};

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <Link className="text-sm text-brand-mid hover:underline" to="/admin">
          ← {t('admin.backHub')}
        </Link>
        <h1 className="text-2xl font-semibold text-brand-dark mt-2">{t('admin.systemSettingsTitle')}</h1>
        <p className="text-slate-600 mt-1">{t('admin.systemSettingsSubtitle')}</p>
      </div>

      <Section title={t('admin.systemSettings.section.app')}>
        <Row label={t('admin.systemSettings.field.applicationName')}>{fmt(data.applicationName)}</Row>
        <Row label={t('admin.systemSettings.field.activeProfiles')}>
          {data.activeProfiles?.length ? data.activeProfiles.join(', ') : '—'}
        </Row>
        <Row label={t('admin.systemSettings.field.serverPort')}>{fmt(data.serverPort)}</Row>
        <Row label={t('admin.systemSettings.field.maxUpload')}>{fmt(data.multipartMaxFileSize)}</Row>
        <Row label={t('admin.systemSettings.field.maxRequest')}>{fmt(data.multipartMaxRequestSize)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.jwt')}>
        <Row label={t('admin.systemSettings.field.accessTokenMinutes')}>{fmt(jwt.accessTokenMinutes)}</Row>
        <Row label={t('admin.systemSettings.field.refreshTokenDays')}>{fmt(jwt.refreshTokenDays)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.ocr')}>
        <Row label={t('admin.systemSettings.field.ocrWorkers')}>{fmt(ocr.workers)}</Row>
        <Row label={t('admin.systemSettings.field.ocrLangDefault')}>{fmt(ocr.langDefault)}</Row>
        <Row label={t('admin.systemSettings.field.ocrTimeoutMinutes')}>{fmt(ocr.timeoutMinutes)}</Row>
        <Row label={t('admin.systemSettings.field.ocrMaxRetries')}>{fmt(ocr.maxRetries)}</Row>
        <Row label={t('admin.systemSettings.field.ocrMock')}>{fmtBool(ocr.mock, t)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.storage')}>
        <Row label={t('admin.systemSettings.field.storageRoot')}>{fmt(storage.rootPath)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.clamav')}>
        <Row label={t('admin.systemSettings.field.clamavEnabled')}>{fmtBool(clamav.enabled, t)}</Row>
        <Row label={t('admin.systemSettings.field.clamavHost')}>{fmt(clamav.host)}</Row>
        <Row label={t('admin.systemSettings.field.clamavPort')}>{fmt(clamav.port)}</Row>
        <Row label={t('admin.systemSettings.field.clamavConnectTimeout')}>{fmt(clamav.connectTimeoutMs)}</Row>
        <Row label={t('admin.systemSettings.field.clamavReadTimeout')}>{fmt(clamav.readTimeoutMs)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.cors')}>
        <Row label={t('admin.systemSettings.field.corsOrigins')}>
          {data.corsAllowedOrigins?.length ? (
            <ul className="list-disc pl-5 space-y-1">
              {data.corsAllowedOrigins.map((o) => (
                <li key={o}>{o}</li>
              ))}
            </ul>
          ) : (
            '—'
          )}
        </Row>
      </Section>

      <Section title={t('admin.systemSettings.section.rateLimit')}>
        <Row label={t('admin.systemSettings.field.authMaxRequests')}>{fmt(rate.maxRequests)}</Row>
        <Row label={t('admin.systemSettings.field.authWindowSeconds')}>{fmt(rate.windowSeconds)}</Row>
      </Section>

      <Section title={t('admin.systemSettings.section.ingest')}>
        <Row label={t('admin.systemSettings.field.ingestEnabled')}>{fmtBool(ingest.enabled, t)}</Row>
        <Row label={t('admin.systemSettings.field.ingestDirectory')}>{fmt(ingest.directory)}</Row>
        <Row label={t('admin.systemSettings.field.ingestIntervalMs')}>{fmt(ingest.intervalMs)}</Row>
        <Row label={t('admin.systemSettings.field.ingestUserId')}>{fmt(ingest.userId)}</Row>
        <Row label={t('admin.systemSettings.field.ingestDocTypeId')}>{fmt(ingest.documentTypeId)}</Row>
        <Row label={t('admin.systemSettings.field.ingestFolderNumber')}>{fmt(ingest.folderNumber)}</Row>
        <Row label={t('admin.systemSettings.field.ingestTitlePrefix')}>{fmt(ingest.titlePrefix)}</Row>
        <Row label={t('admin.systemSettings.field.ingestDocumentDate')}>{fmt(ingest.documentDate)}</Row>
        <Row label={t('admin.systemSettings.field.ingestLanguage')}>{fmt(ingest.language)}</Row>
        <Row label={t('admin.systemSettings.field.ingestConfidentiality')}>{fmt(ingest.confidentiality)}</Row>
        <Row label={t('admin.systemSettings.field.ingestDepartmentId')}>{fmt(ingest.departmentId)}</Row>
        <Row label={t('admin.systemSettings.field.ingestExternalRef')}>{fmt(ingest.externalReference)}</Row>
        <Row label={t('admin.systemSettings.field.ingestAuthor')}>{fmt(ingest.author)}</Row>
        <Row label={t('admin.systemSettings.field.ingestNotes')}>{fmt(ingest.notes)}</Row>
        <Row label={t('admin.systemSettings.field.ingestTags')}>{fmt(ingest.tags)}</Row>
      </Section>

      <p className="text-sm text-slate-500 border-t border-slate-200 pt-4">{t('admin.systemSettingsFootnote')}</p>
    </div>
  );
}
