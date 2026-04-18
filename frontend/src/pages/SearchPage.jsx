import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { searchDocuments } from '../api/searchApi';

const SORTS = ['RELEVANCE', 'DATE_DESC', 'DATE_ASC', 'TITLE_ASC'];

function HighlightSnippet({ value }) {
  if (!value) return null;
  if (value.includes('<mark>')) {
    return (
      <span
        className="search-highlight [&_mark]:bg-amber-200 [&_mark]:rounded-sm"
        dangerouslySetInnerHTML={{ __html: value }}
      />
    );
  }
  return <span className="whitespace-pre-wrap break-words">{value}</span>;
}

export default function SearchPage() {
  const { t } = useTranslation();
  const [q, setQ] = useState('');
  const [folderNumber, setFolderNumber] = useState('');
  const [type, setType] = useState('');
  const [language, setLanguage] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('RELEVANCE');
  const [page, setPage] = useState(0);
  const [searchTick, setSearchTick] = useState(0);

  const requestParams = useMemo(
    () => ({
      q: q.trim() || undefined,
      folderNumber: folderNumber.trim() || undefined,
      type: type ? Number(type) : undefined,
      language: language || undefined,
      status: status || undefined,
      sort,
      page,
      size: 20,
    }),
    [q, folderNumber, type, language, status, sort, page]
  );

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['search', requestParams, searchTick],
    queryFn: () => searchDocuments(requestParams),
  });

  function onSubmitSearch(e) {
    e.preventDefault();
    setPage(0);
    setSearchTick((x) => x + 1);
  }

  const content = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('search.title')}</h1>
      <p className="text-slate-600 mb-6">{t('search.subtitle')}</p>

      <form onSubmit={onSubmitSearch} className="space-y-4 mb-8 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex flex-col sm:flex-row gap-2">
          <input
            className="flex-1 rounded border border-slate-300 px-3 py-2"
            placeholder={t('search.placeholder')}
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <button type="submit" className="rounded bg-brand-mid px-4 py-2 text-white hover:bg-brand-dark">
            {t('search.button')}
          </button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
          <label>
            <span className="text-slate-600 block mb-1">{t('search.folder')}</span>
            <input
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={folderNumber}
              onChange={(e) => setFolderNumber(e.target.value)}
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('search.typeId')}</span>
            <input
              type="number"
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={type}
              onChange={(e) => setType(e.target.value)}
              placeholder="ID"
            />
          </label>
          <label>
            <span className="text-slate-600 block mb-1">{t('search.language')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
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
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="">{t('search.any')}</option>
              <option value="PENDING">PENDING</option>
              <option value="PROCESSING">PROCESSING</option>
              <option value="OCR_SUCCESS">OCR_SUCCESS</option>
              <option value="VALIDATED">VALIDATED</option>
              <option value="ARCHIVED">ARCHIVED</option>
            </select>
          </label>
          <label className="sm:col-span-2">
            <span className="text-slate-600 block mb-1">{t('search.sort')}</span>
            <select
              className="w-full rounded border border-slate-300 px-2 py-1"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              {SORTS.map((s) => (
                <option key={s} value={s}>
                  {t(`search.sorts.${s}`)}
                </option>
              ))}
            </select>
          </label>
        </div>
      </form>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('search.error')}
        </p>
      )}

      <div className="text-sm text-slate-600 mb-2">
        {isFetching ? t('search.loading') : t('search.resultsCount', { count: total })}
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
            </tr>
          </thead>
          <tbody>
            {content.map((row) => (
              <tr key={row.id} className="border-t border-slate-100">
                <td className="p-2 font-medium text-slate-800 max-w-md align-top">
                  <Link className="text-brand-mid hover:underline block" to={`/documents/${row.id}`}>
                    {row.highlightTitle ? (
                      <HighlightSnippet value={row.highlightTitle} />
                    ) : (
                      row.title
                    )}
                  </Link>
                  {row.highlightContent ? (
                    <div className="mt-1.5 text-xs font-normal text-slate-600 leading-snug">
                      <HighlightSnippet value={row.highlightContent} />
                    </div>
                  ) : null}
                </td>
                <td className="p-2 text-slate-600">{row.documentTypeCode}</td>
                <td className="p-2 text-slate-600">{row.folderNumber}</td>
                <td className="p-2 text-slate-600">{row.documentDate}</td>
                <td className="p-2">
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs">{row.status}</span>
                </td>
              </tr>
            ))}
            {!content.length && !isFetching && (
              <tr>
                <td colSpan={5} className="p-6 text-center text-slate-500">
                  {t('search.empty')}
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
