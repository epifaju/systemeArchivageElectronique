import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchDocumentTypes, listDocuments } from '../api/documentApi';

const SORTS = ['DATE_DESC', 'DATE_ASC', 'TITLE_ASC', 'CREATED_DESC', 'CREATED_ASC'];
const PAGE_SIZES = [20, 50, 100];

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

const DEFAULT_SORT = 'DATE_DESC';

function isTypingTarget(el) {
  if (!el) return false;
  const tag = el.tagName?.toLowerCase();
  if (tag === 'textarea' || tag === 'select') return true;
  if (tag === 'input') {
    const type = (el.getAttribute('type') || 'text').toLowerCase();
    if (type === 'button' || type === 'submit' || type === 'checkbox' || type === 'radio') return false;
    return true;
  }
  return el.isContentEditable;
}

function buildActiveFilterChips(applied, types, t) {
  const chips = [];
  if (applied.documentTypeId) {
    const dt = types.find((x) => String(x.id) === String(applied.documentTypeId));
    chips.push({
      key: 'documentTypeId',
      label: `${t('upload.field.type')}: ${dt?.code ?? applied.documentTypeId}`,
    });
  }
  if (applied.folderNumber?.trim()) {
    chips.push({
      key: 'folderNumber',
      label: `${t('search.folder')}: ${applied.folderNumber.trim()}`,
    });
  }
  if (applied.dateFrom) {
    chips.push({
      key: 'dateFrom',
      label: `${t('docList.dateFrom')}: ${applied.dateFrom}`,
    });
  }
  if (applied.dateTo) {
    chips.push({
      key: 'dateTo',
      label: `${t('docList.dateTo')}: ${applied.dateTo}`,
    });
  }
  if (applied.language) {
    const langLabel =
      applied.language === 'FRENCH'
        ? 'FR'
        : applied.language === 'PORTUGUESE'
          ? 'PT'
          : applied.language === 'OTHER'
            ? t('search.other')
            : applied.language === 'MULTILINGUAL'
              ? t('search.multilingual')
              : applied.language;
    chips.push({
      key: 'language',
      label: `${t('search.language')}: ${langLabel}`,
    });
  }
  if (applied.status) {
    chips.push({
      key: 'status',
      label: `${t('search.status')}: ${t(`enums.documentStatus.${applied.status}`)}`,
    });
  }
  if (applied.departmentId?.trim()) {
    chips.push({
      key: 'departmentId',
      label: `${t('docList.departmentId')}: ${applied.departmentId}`,
    });
  }
  if (applied.confidentialityLevel) {
    chips.push({
      key: 'confidentialityLevel',
      label: `${t('upload.field.confidentiality')}: ${t(`enums.confidentiality.${applied.confidentialityLevel}`)}`,
    });
  }
  if (applied.sort && applied.sort !== DEFAULT_SORT) {
    chips.push({
      key: 'sort',
      label: `${t('search.sort')}: ${t(`search.sorts.${applied.sort}`)}`,
    });
  }
  return chips;
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

function nextSort(current, col) {
  if (col === 'date') {
    return current === 'DATE_DESC' ? 'DATE_ASC' : 'DATE_DESC';
  }
  if (col === 'title') {
    return 'TITLE_ASC';
  }
  return current;
}

function SortHeader({ label, active, direction, onClick }) {
  const icon = !active ? '↕' : direction === 'asc' ? '↑' : '↓';
  const base =
    'inline-flex items-center gap-1 rounded px-1.5 py-1 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2';
  return (
    <button type="button" className={base} onClick={onClick}>
      <span>{label}</span>
      <span className="text-[11px] text-slate-500">{icon}</span>
    </button>
  );
}

function StatusBadge({ status, t }) {
  const tone =
    status === 'OCR_FAILED'
      ? 'bg-red-50 text-red-900 border-red-200'
      : status === 'PENDING'
        ? 'bg-amber-50 text-amber-900 border-amber-200'
        : status === 'PROCESSING'
          ? 'bg-sky-50 text-sky-900 border-sky-200'
          : status === 'OCR_PARTIAL' || status === 'NEEDS_REVIEW'
            ? 'bg-orange-50 text-orange-900 border-orange-200'
            : status === 'OCR_SUCCESS' || status === 'VALIDATED' || status === 'ARCHIVED'
              ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
              : 'bg-slate-50 text-slate-900 border-slate-200';

  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium ${tone}`}>
      {t(`enums.documentStatus.${status}`)}
    </span>
  );
}

export default function DocumentListPage() {
  const { t, i18n } = useTranslation();
  const [form, setForm] = useState(emptyFilters);
  const [applied, setApplied] = useState(emptyFilters);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [showFilters, setShowFilters] = useState(true);
  const folderInputRef = useRef(null);
  const filterPanelRef = useRef(null);
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';

  const { data: types = [] } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
  });

  const activeChips = useMemo(
    () => buildActiveFilterChips(applied, types, t),
    [applied, types, t]
  );

  function removeFilterChip(key) {
    const empty = emptyFilters();
    const nextVal = key === 'sort' ? DEFAULT_SORT : empty[key];
    setApplied((s) => ({ ...s, [key]: nextVal }));
    setForm((s) => ({ ...s, [key]: nextVal }));
    setPage(0);
  }

  useEffect(() => {
    function onKeyDown(e) {
      if (e.defaultPrevented) return;
      if (e.ctrlKey || e.metaKey || e.altKey) return;

      if (e.key === '/' && !e.shiftKey) {
        if (isTypingTarget(document.activeElement)) return;
        e.preventDefault();
        setShowFilters(true);
        requestAnimationFrame(() => {
          folderInputRef.current?.focus();
        });
        return;
      }

      if (e.key === 'Escape' && showFilters) {
        if (filterPanelRef.current?.contains(document.activeElement)) {
          e.preventDefault();
          setShowFilters(false);
        }
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [showFilters]);

  const requestParams = useMemo(() => {
    const p = {
      sort: applied.sort,
      page,
      size,
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
  }, [applied, page, size]);

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['documents', requestParams],
    queryFn: () => listDocuments(requestParams),
  });

  function onSubmitFilters(e) {
    e.preventDefault();
    setApplied({ ...form });
    setPage(0);
  }

  function onReset() {
    const next = emptyFilters();
    setForm(next);
    setApplied(next);
    setPage(0);
  }

  const content = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  const typeLabel = (row) =>
    i18n.language?.startsWith('pt') ? row.documentTypeLabelPt : row.documentTypeLabelFr;

  const sort = applied.sort;
  const dateSortActive = sort === 'DATE_ASC' || sort === 'DATE_DESC';
  const dateDir = sort === 'DATE_ASC' ? 'asc' : 'desc';
  const titleSortActive = sort === 'TITLE_ASC';

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-brand-dark">{t('docList.title')}</h1>
          <p className="text-slate-600 mt-1">{t('docList.subtitle')}</p>
        </div>
        <div className="text-sm text-slate-600">
          {isFetching ? t('search.loading') : t('docList.count', { count: total })}
        </div>
      </div>

      <div className="mb-4 rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="text-sm font-medium text-slate-900 rounded-md px-2 py-1 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={() => setShowFilters((v) => !v)}
            >
              {showFilters ? t('docList.hideFilters') : t('docList.showFilters')}
            </button>
            <span className="text-xs text-slate-500 hidden sm:inline">
              {t('docList.denseHint')} {t('docList.filterShortcutHint')}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <span className="hidden sm:inline">{t('docList.pageSize')}</span>
              <select
                className="rounded border border-slate-200 bg-white px-2 py-1.5 text-sm"
                value={size}
                onChange={(e) => {
                  setSize(Number(e.target.value));
                  setPage(0);
                }}
              >
                {PAGE_SIZES.map((n) => (
                  <option key={n} value={n}>
                    {t('docList.pageSizeValue', { count: n })}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="text-sm rounded-md border border-slate-200 px-3 py-1.5 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={onReset}
            >
              {t('docList.reset')}
            </button>
            <button
              type="button"
              className="text-sm rounded-md bg-brand-mid px-3 py-1.5 text-white hover:bg-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={() => {
                setApplied({ ...form });
                setPage(0);
              }}
            >
              {t('docList.apply')}
            </button>
          </div>
        </div>

        {showFilters && (
          <form ref={filterPanelRef} onSubmit={onSubmitFilters} className="px-4 py-3">
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 text-xs">
              <label className="space-y-1">
                <span className="text-slate-600">{t('upload.field.type')}</span>
                <select
                  className="w-full rounded border border-slate-300 px-2 py-1.5 bg-white"
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

              <label className="space-y-1">
                <span className="text-slate-600">{t('search.folder')}</span>
                <input
                  ref={folderInputRef}
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.folderNumber}
                  onChange={(e) => setForm((s) => ({ ...s, folderNumber: e.target.value }))}
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('docList.dateFrom')}</span>
                <input
                  type="date"
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.dateFrom}
                  onChange={(e) => setForm((s) => ({ ...s, dateFrom: e.target.value }))}
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('docList.dateTo')}</span>
                <input
                  type="date"
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.dateTo}
                  onChange={(e) => setForm((s) => ({ ...s, dateTo: e.target.value }))}
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('search.language')}</span>
                <select
                  className="w-full rounded border border-slate-300 px-2 py-1.5 bg-white"
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

              <label className="space-y-1">
                <span className="text-slate-600">{t('search.status')}</span>
                <select
                  className="w-full rounded border border-slate-300 px-2 py-1.5 bg-white"
                  value={form.status}
                  onChange={(e) => setForm((s) => ({ ...s, status: e.target.value }))}
                >
                  <option value="">{t('search.any')}</option>
                  <option value="PENDING">{t('enums.documentStatus.PENDING')}</option>
                  <option value="PROCESSING">{t('enums.documentStatus.PROCESSING')}</option>
                  <option value="OCR_SUCCESS">{t('enums.documentStatus.OCR_SUCCESS')}</option>
                  <option value="OCR_PARTIAL">{t('enums.documentStatus.OCR_PARTIAL')}</option>
                  <option value="OCR_FAILED">{t('enums.documentStatus.OCR_FAILED')}</option>
                  <option value="NEEDS_REVIEW">{t('enums.documentStatus.NEEDS_REVIEW')}</option>
                  <option value="VALIDATED">{t('enums.documentStatus.VALIDATED')}</option>
                  <option value="ARCHIVED">{t('enums.documentStatus.ARCHIVED')}</option>
                </select>
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('docList.departmentId')}</span>
                <input
                  type="number"
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.departmentId}
                  onChange={(e) => setForm((s) => ({ ...s, departmentId: e.target.value }))}
                  placeholder="—"
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('upload.field.confidentiality')}</span>
                <select
                  className="w-full rounded border border-slate-300 px-2 py-1.5 bg-white"
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

              <label className="space-y-1 sm:col-span-2 lg:col-span-4">
                <span className="text-slate-600">{t('search.sort')}</span>
                <select
                  className="w-full rounded border border-slate-300 px-2 py-1.5 bg-white"
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

            <div className="pt-3 flex items-center gap-2">
              <button
                type="submit"
                className="text-sm rounded-md bg-brand-mid px-3 py-1.5 text-white hover:bg-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              >
                {t('docList.apply')}
              </button>
              <button
                type="button"
                className="text-sm rounded-md border border-slate-200 px-3 py-1.5 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
                onClick={onReset}
              >
                {t('docList.reset')}
              </button>
            </div>
          </form>
        )}
      </div>

      {activeChips.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-slate-600">{t('docList.activeFilters')}</span>
          {activeChips.map((chip) => (
            <button
              key={chip.key}
              type="button"
              onClick={() => removeFilterChip(chip.key)}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-left text-[11px] font-medium text-slate-800 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-1"
              title={t('docList.removeFilterChip')}
              aria-label={`${t('docList.removeFilterChip')}: ${chip.label}`}
            >
              <span className="truncate">{chip.label}</span>
              <span className="shrink-0 text-slate-500" aria-hidden>
                ×
              </span>
            </button>
          ))}
        </div>
      )}

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('docList.error')}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left sticky top-[56px] z-10">
            <tr>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">
                <SortHeader
                  label={t('search.col.title')}
                  active={titleSortActive}
                  direction="asc"
                  onClick={() => {
                    setApplied((s) => ({ ...s, sort: nextSort(s.sort, 'title') }));
                    setForm((s) => ({ ...s, sort: nextSort(s.sort, 'title') }));
                    setPage(0);
                  }}
                />
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.type')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.folder')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">
                <SortHeader
                  label={t('search.col.date')}
                  active={dateSortActive}
                  direction={dateDir}
                  onClick={() => {
                    setApplied((s) => ({ ...s, sort: nextSort(s.sort, 'date') }));
                    setForm((s) => ({ ...s, sort: nextSort(s.sort, 'date') }));
                    setPage(0);
                  }}
                />
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.status')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('docList.col.mime')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('docList.col.confidentiality')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700 w-20">{t('docList.col.action')}</th>
            </tr>
          </thead>
          <tbody>
            {content.map((row) => (
              <tr key={row.id} className="border-t border-slate-100 hover:bg-slate-50/60">
                <td className="px-3 py-2 font-medium text-slate-900 max-w-[420px]">
                  <Link className="text-brand-mid hover:underline" to={`/documents/${row.id}`}>
                    {row.title}
                  </Link>
                </td>
                <td className="px-3 py-2 text-slate-700 whitespace-nowrap">{typeLabel(row) || row.documentTypeCode}</td>
                <td className="px-3 py-2 text-slate-700 whitespace-nowrap">{row.folderNumber || '—'}</td>
                <td className="px-3 py-2 text-slate-700 whitespace-nowrap">{formatDocDate(row.documentDate, locale)}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  <StatusBadge status={row.status} t={t} />
                </td>
                <td className="px-3 py-2 text-slate-600 text-xs max-w-[160px] truncate" title={row.mimeType}>
                  {row.mimeType || '—'}
                </td>
                <td className="px-3 py-2 text-xs text-slate-700 whitespace-nowrap">
                  {t(`enums.confidentiality.${row.confidentialityLevel}`)}
                </td>
                <td className="px-3 py-2 text-xs whitespace-nowrap">
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
        <div className="mt-4 flex items-center justify-between gap-2">
          <div className="text-xs text-slate-500">
            {t('docList.page', { page: page + 1, totalPages })}
          </div>
          <button
            type="button"
            disabled={page <= 0}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40 hover:bg-slate-50"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {t('search.prev')}
          </button>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40 hover:bg-slate-50"
            onClick={() => setPage((p) => p + 1)}
          >
            {t('search.next')}
          </button>
        </div>
      )}
    </div>
  );
}
