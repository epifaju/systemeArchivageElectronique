import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import DOMPurify from 'dompurify';
import { fetchDocumentTypes } from '../api/documentApi';
import { createSavedSearch, deleteSavedSearch, fetchSavedSearches } from '../api/savedSearchApi';
import { downloadSearchCsv, searchDocuments } from '../api/searchApi';

const SORTS = ['RELEVANCE', 'DATE_DESC', 'DATE_ASC', 'TITLE_ASC'];
const PAGE_SIZES = [20, 50, 100];
const DEFAULT_SORT = 'RELEVANCE';

function emptySearchFilters() {
  return {
    q: '',
    folderNumber: '',
    documentTypeId: '',
    dateFrom: '',
    dateTo: '',
    language: '',
    status: '',
    departmentId: '',
    confidentialityLevel: '',
    sort: DEFAULT_SORT,
  };
}

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

function buildActiveSearchChips(applied, types, t) {
  const chips = [];
  if (applied.q?.trim()) {
    chips.push({ key: 'q', label: `${t('search.placeholder')}: ${applied.q.trim()}` });
  }
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
    chips.push({ key: 'dateFrom', label: `${t('search.dateFrom')}: ${applied.dateFrom}` });
  }
  if (applied.dateTo) {
    chips.push({ key: 'dateTo', label: `${t('search.dateTo')}: ${applied.dateTo}` });
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
    chips.push({ key: 'language', label: `${t('search.language')}: ${langLabel}` });
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
      label: `${t('search.departmentId')}: ${applied.departmentId}`,
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

function HighlightSnippet({ value }) {
  if (!value) return null;
  if (value.includes('<mark>')) {
    const clean = DOMPurify.sanitize(value, { ALLOWED_TAGS: ['mark'], ALLOWED_ATTR: [] });
    return (
      <span
        className="search-highlight [&_mark]:bg-amber-200 [&_mark]:rounded-sm"
        dangerouslySetInnerHTML={{ __html: clean }}
      />
    );
  }
  return <span className="whitespace-pre-wrap break-words">{value}</span>;
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

function formatSavedDate(iso, locale) {
  if (!iso) return '';
  try {
    return new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));
  } catch {
    return String(iso);
  }
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

export default function SearchPage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [form, setForm] = useState(emptySearchFilters);
  const [applied, setApplied] = useState(emptySearchFilters);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [showFilters, setShowFilters] = useState(true);
  const [csvExporting, setCsvExporting] = useState(false);
  const [csvError, setCsvError] = useState(null);
  const [saveName, setSaveName] = useState('');
  const qInputRef = useRef(null);
  const filterPanelRef = useRef(null);
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';

  const { data: types = [] } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
  });

  const requestParams = useMemo(() => {
    const p = {
      sort: applied.sort,
      page,
      size,
    };
    const qt = applied.q.trim();
    if (qt) p.q = qt;
    const fn = applied.folderNumber.trim();
    if (fn) p.folderNumber = fn;
    if (applied.documentTypeId) p.type = Number(applied.documentTypeId);
    if (applied.dateFrom) p.dateFrom = applied.dateFrom;
    if (applied.dateTo) p.dateTo = applied.dateTo;
    if (applied.language) p.language = applied.language;
    if (applied.status) p.status = applied.status;
    if (applied.departmentId.trim()) p.departmentId = Number(applied.departmentId);
    if (applied.confidentialityLevel) p.confidentialityLevel = applied.confidentialityLevel;
    return p;
  }, [applied, page, size]);

  const csvExportParams = useMemo(() => {
    const p = { sort: applied.sort, max: 5000 };
    const qt = applied.q.trim();
    if (qt) p.q = qt;
    const fn = applied.folderNumber.trim();
    if (fn) p.folderNumber = fn;
    if (applied.documentTypeId) p.type = Number(applied.documentTypeId);
    if (applied.dateFrom) p.dateFrom = applied.dateFrom;
    if (applied.dateTo) p.dateTo = applied.dateTo;
    if (applied.language) p.language = applied.language;
    if (applied.status) p.status = applied.status;
    if (applied.departmentId.trim()) p.departmentId = Number(applied.departmentId);
    if (applied.confidentialityLevel) p.confidentialityLevel = applied.confidentialityLevel;
    return p;
  }, [applied]);

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['search', requestParams],
    queryFn: () => searchDocuments(requestParams),
  });

  const activeChips = useMemo(
    () => buildActiveSearchChips(applied, types, t),
    [applied, types, t]
  );

  function removeFilterChip(key) {
    const empty = emptySearchFilters();
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
        requestAnimationFrame(() => qInputRef.current?.focus());
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

  function buildCriteriaForApi() {
    const c = {};
    const qt = applied.q.trim();
    if (qt) c.q = qt;
    const fn = applied.folderNumber.trim();
    if (fn) c.folderNumber = fn;
    if (applied.documentTypeId) c.type = Number(applied.documentTypeId);
    if (applied.dateFrom) c.dateFrom = applied.dateFrom;
    if (applied.dateTo) c.dateTo = applied.dateTo;
    if (applied.language) c.language = applied.language;
    if (applied.status) c.status = applied.status;
    if (applied.departmentId.trim()) c.departmentId = Number(applied.departmentId);
    if (applied.confidentialityLevel) c.confidentialityLevel = applied.confidentialityLevel;
    if (applied.sort) c.sort = applied.sort;
    return c;
  }

  function criteriaToFormState(o) {
    const base = emptySearchFilters();
    if (!o || typeof o !== 'object') return base;
    return {
      ...base,
      q: typeof o.q === 'string' ? o.q : base.q,
      folderNumber: typeof o.folderNumber === 'string' ? o.folderNumber : base.folderNumber,
      documentTypeId:
        o.type != null && o.type !== ''
          ? String(o.type)
          : o.documentTypeId != null && o.documentTypeId !== ''
            ? String(o.documentTypeId)
            : base.documentTypeId,
      dateFrom: typeof o.dateFrom === 'string' ? o.dateFrom : base.dateFrom,
      dateTo: typeof o.dateTo === 'string' ? o.dateTo : base.dateTo,
      language: typeof o.language === 'string' ? o.language : base.language,
      status: typeof o.status === 'string' ? o.status : base.status,
      departmentId:
        typeof o.departmentId === 'string'
          ? o.departmentId
          : o.departmentId != null
            ? String(o.departmentId)
            : base.departmentId,
      confidentialityLevel:
        typeof o.confidentialityLevel === 'string' ? o.confidentialityLevel : base.confidentialityLevel,
      sort: typeof o.sort === 'string' ? o.sort : base.sort,
    };
  }

  function applySavedCriteria(cr) {
    const next = criteriaToFormState(cr);
    setForm(next);
    setApplied(next);
    setPage(0);
  }

  const { data: savedList = [], isLoading: savedLoading } = useQuery({
    queryKey: ['saved-searches'],
    queryFn: fetchSavedSearches,
  });

  const saveMutation = useMutation({
    mutationFn: (name) => createSavedSearch({ name, criteria: buildCriteriaForApi() }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['saved-searches'] });
      setSaveName('');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => deleteSavedSearch(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['saved-searches'] }),
  });

  function onSaveSearch(e) {
    e.preventDefault();
    const name = saveName.trim();
    if (!name) return;
    saveMutation.mutate(name);
  }

  function onSubmitSearch(e) {
    e.preventDefault();
    setApplied({ ...form });
    setPage(0);
  }

  function onReset() {
    const next = emptySearchFilters();
    setForm(next);
    setApplied(next);
    setPage(0);
  }

  async function onExportCsv() {
    setCsvError(null);
    setCsvExporting(true);
    try {
      const blob = await downloadSearchCsv(csvExportParams);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'recherche-documents.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setCsvError(err?.message || t('search.exportCsvError'));
    } finally {
      setCsvExporting(false);
    }
  }

  const content = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-brand-dark">{t('search.title')}</h1>
          <p className="text-slate-600 mt-1 text-sm">{t('search.subtitle')}</p>
        </div>
        <div className="text-sm text-slate-600">
          {isFetching ? t('search.loading') : t('search.resultsCount', { count: total })}
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
              {showFilters ? t('search.hideFilters') : t('search.showFilters')}
            </button>
            <span className="text-xs text-slate-500 hidden sm:inline">
              {t('search.denseHint')} {t('search.filterShortcutHint')}
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <span className="hidden sm:inline">{t('search.pageSize')}</span>
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
                    {t('search.pageSizeValue', { count: n })}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="text-sm rounded-md border border-slate-200 px-3 py-1.5 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={onReset}
            >
              {t('search.reset')}
            </button>
            <button
              type="button"
              className="text-sm rounded-md border border-slate-200 px-3 py-1.5 hover:bg-slate-50 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              disabled={csvExporting}
              onClick={() => onExportCsv()}
            >
              {csvExporting ? t('search.exportCsvLoading') : t('search.exportCsv')}
            </button>
            <button
              type="button"
              className="text-sm rounded-md bg-brand-mid px-3 py-1.5 text-white hover:bg-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={() => {
                setApplied({ ...form });
                setPage(0);
              }}
            >
              {t('search.apply')}
            </button>
          </div>
        </div>

        {showFilters && (
          <form ref={filterPanelRef} onSubmit={onSubmitSearch} className="px-4 py-3 border-b border-slate-100">
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 text-xs">
              <label className="space-y-1 sm:col-span-2 lg:col-span-4">
                <span className="text-slate-600">{t('search.placeholder')}</span>
                <input
                  ref={qInputRef}
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  placeholder={t('search.placeholder')}
                  value={form.q}
                  onChange={(e) => setForm((s) => ({ ...s, q: e.target.value }))}
                />
              </label>

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
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.folderNumber}
                  onChange={(e) => setForm((s) => ({ ...s, folderNumber: e.target.value }))}
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('search.dateFrom')}</span>
                <input
                  type="date"
                  className="w-full rounded border border-slate-300 px-2 py-1.5"
                  value={form.dateFrom}
                  onChange={(e) => setForm((s) => ({ ...s, dateFrom: e.target.value }))}
                />
              </label>

              <label className="space-y-1">
                <span className="text-slate-600">{t('search.dateTo')}</span>
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
                <span className="text-slate-600">{t('search.departmentId')}</span>
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

            <div className="pt-3 flex flex-wrap items-center gap-2">
              <button
                type="submit"
                className="text-sm rounded-md bg-brand-mid px-3 py-1.5 text-white hover:bg-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              >
                {t('search.button')}
              </button>
              <button
                type="button"
                className="text-sm rounded-md border border-slate-200 px-3 py-1.5 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
                onClick={onReset}
              >
                {t('search.reset')}
              </button>
              <p className="text-xs text-slate-500 w-full sm:w-auto sm:ml-2">{t('search.exportCsvHint')}</p>
            </div>
          </form>
        )}
      </div>

      {activeChips.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-slate-600">{t('search.activeFilters')}</span>
          {activeChips.map((chip) => (
            <button
              key={chip.key}
              type="button"
              onClick={() => removeFilterChip(chip.key)}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-left text-[11px] font-medium text-slate-800 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-1"
              title={t('search.removeFilterChip')}
              aria-label={`${t('search.removeFilterChip')}: ${chip.label}`}
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
          {error?.response?.data?.message || error?.message || t('search.error')}
        </p>
      )}
      {csvError && <p className="text-red-600 text-sm mb-4">{csvError}</p>}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full text-sm border-separate border-spacing-0">
          <thead className="bg-slate-50 text-left shadow-[0_1px_0_0_rgb(241_245_249)]">
            <tr>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.title')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.type')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.folder')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.date')}</th>
              <th className="px-3 py-2 text-xs font-semibold text-slate-700">{t('search.col.status')}</th>
            </tr>
          </thead>
          <tbody>
            {content.map((row, index) => {
              const isFirst = index === 0;
              const cellY = isFirst ? 'pt-3 pb-2' : 'py-2';
              return (
              <tr key={row.id} className="border-t border-slate-100 hover:bg-slate-50/60">
                <td className={`px-3 ${cellY} font-medium text-slate-900 max-w-md align-top`}>
                  <Link
                    className="text-brand-mid hover:underline block leading-normal"
                    to={`/documents/${row.id}`}
                  >
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
                <td className={`px-3 ${cellY} text-slate-700 whitespace-nowrap`}>{row.documentTypeCode}</td>
                <td className={`px-3 ${cellY} text-slate-700 whitespace-nowrap`}>{row.folderNumber || '—'}</td>
                <td className={`px-3 ${cellY} text-slate-700 whitespace-nowrap`}>
                  {formatDocDate(row.documentDate, locale)}
                </td>
                <td className={`px-3 ${cellY} whitespace-nowrap`}>
                  <StatusBadge status={row.status} t={t} />
                </td>
              </tr>
              );
            })}
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
        <div className="mt-4 flex items-center justify-between gap-2 flex-wrap">
          <div className="text-xs text-slate-500">
            {t('search.page', { page: page + 1, totalPages })}
          </div>
          <div className="flex items-center gap-2">
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
        </div>
      )}

      <section className="mt-8 rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="px-4 py-3 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-brand-dark">{t('search.savedTitle')}</h2>
        </div>
        <div className="px-4 py-3">
          <form onSubmit={onSaveSearch} className="flex flex-col sm:flex-row gap-2 mb-4">
            <input
              className="flex-1 rounded border border-slate-300 px-2 py-1.5 text-sm"
              placeholder={t('search.savedNamePlaceholder')}
              value={saveName}
              onChange={(e) => setSaveName(e.target.value)}
              maxLength={200}
            />
            <button
              type="submit"
              className="rounded-md bg-slate-700 px-3 py-1.5 text-sm text-white hover:bg-slate-800 disabled:opacity-50"
              disabled={saveMutation.isPending || !saveName.trim()}
            >
              {saveMutation.isPending ? t('search.savedSaving') : t('search.savedSave')}
            </button>
          </form>
          {saveMutation.isError && (
            <p className="text-red-600 text-sm mb-2">
              {saveMutation.error?.response?.data?.message ||
                saveMutation.error?.message ||
                t('search.savedSaveError')}
            </p>
          )}
          {savedLoading ? (
            <p className="text-sm text-slate-500">{t('search.savedLoading')}</p>
          ) : savedList.length === 0 ? (
            <p className="text-sm text-slate-500">{t('search.savedEmpty')}</p>
          ) : (
            <ul className="divide-y divide-slate-100 border border-slate-200 rounded-md text-sm">
              {savedList.map((s) => (
                <li
                  key={s.id}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 px-3 py-2"
                >
                  <div>
                    <span className="font-medium text-slate-800">{s.name}</span>
                    <span className="text-slate-500 ml-2 text-xs">
                      {formatSavedDate(s.updatedAt, i18n.language)}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="rounded border border-brand-mid px-2 py-1 text-xs text-brand-mid hover:bg-slate-50"
                      onClick={() => applySavedCriteria(s.criteria)}
                    >
                      {t('search.savedApply')}
                    </button>
                    <button
                      type="button"
                      className="rounded border border-red-200 px-2 py-1 text-xs text-red-800 hover:bg-red-50 disabled:opacity-50"
                      disabled={deleteMutation.isPending}
                      onClick={() => {
                        if (window.confirm(t('search.savedConfirmDelete'))) {
                          deleteMutation.mutate(s.id);
                        }
                      }}
                    >
                      {t('search.savedDelete')}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}
