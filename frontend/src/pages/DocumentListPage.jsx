import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchDocumentTypes, listDocuments } from '../api/documentApi';

const SORTS = ['DATE_DESC', 'DATE_ASC', 'TITLE_ASC'];

function emptyFilters() {
  return {
    documentTypeId: '',
    folderNumber: '',
    dateFrom: '',
    dateTo: '',
    language: '',
    status: '',
    departmentId: '',
    confidentialityLevel: '',
    sort: 'DATE_DESC',
  };
}

export default function DocumentListPage() {
  const { t, i18n } = useTranslation();
  const [form, setForm] = useState(emptyFilters);
  const [applied, setApplied] = useState(emptyFilters);
  const [page, setPage] = useState(0);

  const { data: types = [] } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
  });

  const requestParams = useMemo(() => {
    const p = {
      sort: applied.sort,
      page,
      size: 20,
    };
    if (applied.documentTypeId) p.documentTypeId = Number(applied.documentTypeId);
    if (applied.folderNumber.trim()) p.folderNumber = applied.folderNumber.trim();
    if (applied.dateFrom) p.dateFrom = applied.dateFrom;
    if (applied.dateTo) p.dateTo = applied.dateTo;
    if (applied.language) p.language = applied.language;
    if (applied.status) p.status = applied.status;
    if (applied.departmentId.trim()) p.departmentId = Number(applied.departmentId);
    if (applied.confidentialityLevel) p.confidentialityLevel = applied.confidentialityLevel;
    return p;
  }, [applied, page]);

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['documents', requestParams],
    queryFn: () => listDocuments(requestParams),
  });

  function onSubmitFilters(e) {
    e.preventDefault();
    setApplied({ ...form });
    setPage(0);
  }

  const content = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  const typeLabel = (row) =>
    i18n.language?.startsWith('pt') ? row.documentTypeLabelPt : row.documentTypeLabelFr;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('docList.title')}</h1>
      <p className="text-slate-600 mb-6">{t('docList.subtitle')}</p>

      <form
        onSubmit={onSubmitFilters}
        className="space-y-4 mb-8 rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
      >
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
          <label>
            <span className="text-slate-600 block mb-1">{t('upload.field.type')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.documentTypeId}
              onChange={(e) => setForm((s) => ({ ...s, documentTypeId: e.target.value }))}
            >
              <option value="">{t('search.any')}</option>
              {types.map((dt) => (
                <option key={dt.id} value={dt.id}>
                  {dt.code}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('search.folder')}</span>
            <input
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.folderNumber}
              onChange={(e) => setForm((s) => ({ ...s, folderNumber: e.target.value }))}
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('docList.dateFrom')}</span>
            <input
              type="date"
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.dateFrom}
              onChange={(e) => setForm((s) => ({ ...s, dateFrom: e.target.value }))}
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('docList.dateTo')}</span>
            <input
              type="date"
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.dateTo}
              onChange={(e) => setForm((s) => ({ ...s, dateTo: e.target.value }))}
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('search.language')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.language}
              onChange={(e) => setForm((s) => ({ ...s, language: e.target.value }))}
            >
              <option value="">{t('search.any')}</option>
              <option value="FRENCH">FR</option>
              <option value="PORTUGUESE">PT</option>
              <option value="OTHER">{t('search.other')}</option>
              <option value="MULTILINGUAL">{t('search.multilingual')}</option>
            </select>
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('search.status')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.status}
              onChange={(e) => setForm((s) => ({ ...s, status: e.target.value }))}
            >
              <option value="">{t('search.any')}</option>
              <option value="PENDING">PENDING</option>
              <option value="PROCESSING">PROCESSING</option>
              <option value="OCR_SUCCESS">OCR_SUCCESS</option>
              <option value="OCR_PARTIAL">OCR_PARTIAL</option>
              <option value="OCR_FAILED">OCR_FAILED</option>
              <option value="VALIDATED">VALIDATED</option>
              <option value="ARCHIVED">ARCHIVED</option>
            </select>
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('docList.departmentId')}</span>
            <input
              type="number"
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.departmentId}
              onChange={(e) => setForm((s) => ({ ...s, departmentId: e.target.value }))}
              placeholder="—"
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('upload.field.confidentiality')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.confidentialityLevel}
              onChange={(e) => setForm((s) => ({ ...s, confidentialityLevel: e.target.value }))}
            >
              <option value="">{t('search.any')}</option>
              <option value="PUBLIC">{t('enums.confidentiality.PUBLIC')}</option>
              <option value="INTERNAL">{t('enums.confidentiality.INTERNAL')}</option>
              <option value="CONFIDENTIAL">{t('enums.confidentiality.CONFIDENTIAL')}</option>
              <option value="SECRET">{t('enums.confidentiality.SECRET')}</option>
            </select>
          </label>
          <label className="sm:col-span-2">
            <span className="text-slate-600 block mb-1">{t('search.sort')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={form.sort}
              onChange={(e) => setForm((s) => ({ ...s, sort: e.target.value }))}
            >
              {SORTS.map((s) => (
                <option key={s} value={s}>
                  {t(`search.sorts.${s}`)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="submit" className="rounded bg-brand-mid px-4 py-2 text-sm text-white hover:bg-brand-dark">
          {t('docList.apply')}
        </button>
      </form>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('docList.error')}
        </p>
      )}

      <div className="text-sm text-slate-600 mb-2">
        {isFetching ? t('search.loading') : t('docList.count', { count: total })}
      </div>

      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left">
            <tr>
              <th className="p-2">{t('search.col.title')}</th>
              <th className="p-2">{t('search.col.type')}</th>
              <th className="p-2">{t('search.col.folder')}</th>
              <th className="p-2">{t('search.col.date')}</th>
              <th className="p-2">{t('search.col.status')}</th>
              <th className="p-2">{t('docList.col.mime')}</th>
              <th className="p-2">{t('docList.col.confidentiality')}</th>
              <th className="p-2 w-24">{t('docList.col.action')}</th>
            </tr>
          </thead>
          <tbody>
            {content.map((row) => (
              <tr key={row.id} className="border-t border-slate-100">
                <td className="p-2 font-medium text-slate-800">
                  <Link className="text-brand-mid hover:underline" to={`/documents/${row.id}`}>
                    {row.title}
                  </Link>
                </td>
                <td className="p-2 text-slate-600">{typeLabel(row) || row.documentTypeCode}</td>
                <td className="p-2 text-slate-600">{row.folderNumber}</td>
                <td className="p-2 text-slate-600">{row.documentDate}</td>
                <td className="p-2">
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs">{row.status}</span>
                </td>
                <td className="p-2 text-slate-500 text-xs max-w-[140px] truncate" title={row.mimeType}>
                  {row.mimeType || '—'}
                </td>
                <td className="p-2 text-xs">{t(`enums.confidentiality.${row.confidentialityLevel}`)}</td>
                <td className="p-2">
                  <Link
                    className="text-brand-mid hover:underline text-xs"
                    to={`/documents/${row.id}`}
                  >
                    {t('docList.open')}
                  </Link>
                </td>
              </tr>
            ))}
            {!content.length && !isFetching && (
              <tr>
                <td colSpan={8} className="p-6 text-center text-slate-500">
                  {t('docList.empty')}
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
    </div>
  );
}
