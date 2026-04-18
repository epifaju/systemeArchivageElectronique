import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { createDocumentType, listDocumentTypes } from '../api/adminApi';

export default function AdminDocumentTypesPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [code, setCode] = useState('');
  const [labelFr, setLabelFr] = useState('');
  const [labelPt, setLabelPt] = useState('');
  const [active, setActive] = useState(true);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-document-types', page],
    queryFn: () => listDocumentTypes(page, 50),
  });

  const createMut = useMutation({
    mutationFn: createDocumentType,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-document-types'] });
      qc.invalidateQueries({ queryKey: ['document-types'] });
      setCode('');
      setLabelFr('');
      setLabelPt('');
      setActive(true);
    },
  });

  function onCreate(e) {
    e.preventDefault();
    createMut.mutate({
      code: code.trim(),
      labelFr: labelFr.trim(),
      labelPt: labelPt.trim(),
      active,
    });
  }

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <Link className="text-sm text-brand-mid hover:underline" to="/admin">
        ← {t('admin.backHub')}
      </Link>
      <h1 className="text-2xl font-semibold text-brand-dark mt-2 mb-6">{t('admin.docTypesTitle')}</h1>

      <div className="rounded-lg border border-slate-200 bg-white p-4 mb-8 shadow-sm">
        <h2 className="font-semibold text-slate-800 mb-3">{t('admin.docTypesCreate')}</h2>
        <form onSubmit={onCreate} className="grid gap-3 sm:grid-cols-2 text-sm">
          <label className="block sm:col-span-2">
            <span className="text-slate-600">{t('admin.dt.code')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
              placeholder="EX: COURRIER"
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.dt.labelFr')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={labelFr}
              onChange={(e) => setLabelFr(e.target.value)}
              required
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.dt.labelPt')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={labelPt}
              onChange={(e) => setLabelPt(e.target.value)}
              required
            />
          </label>
          <label className="flex items-center gap-2 sm:col-span-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            <span className="text-slate-600">{t('admin.dt.active')}</span>
          </label>
          {createMut.isError && (
            <p className="text-red-600 text-xs sm:col-span-2">
              {createMut.error?.response?.data?.message || createMut.error?.message}
            </p>
          )}
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={createMut.isPending}
              className="rounded bg-brand-mid px-4 py-2 text-sm text-white hover:bg-brand-dark disabled:opacity-50"
            >
              {t('admin.docTypesSubmit')}
            </button>
          </div>
        </form>
      </div>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('admin.loadError')}
        </p>
      )}

      <h2 className="font-semibold text-slate-800 mb-2">{t('admin.docTypesList')}</h2>
      {isLoading ? (
        <p className="text-slate-600">{t('search.loading')}</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left">
                <tr>
                  <th className="p-2">{t('admin.dt.code')}</th>
                  <th className="p-2">{t('admin.dt.labelFr')}</th>
                  <th className="p-2">{t('admin.dt.labelPt')}</th>
                  <th className="p-2">{t('admin.dt.active')}</th>
                </tr>
              </thead>
              <tbody>
                {content.map((dt) => (
                  <tr key={dt.id} className="border-t border-slate-100">
                    <td className="p-2 font-mono text-xs">{dt.code}</td>
                    <td className="p-2">{dt.labelFr}</td>
                    <td className="p-2">{dt.labelPt}</td>
                    <td className="p-2">{dt.active ? t('admin.yes') : t('admin.no')}</td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={4} className="p-6 text-center text-slate-500">
                      {t('admin.docTypesEmpty')}
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
