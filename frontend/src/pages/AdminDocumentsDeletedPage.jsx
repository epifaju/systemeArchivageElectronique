import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { listDeletedDocuments, restoreDocument } from '../api/adminApi';

function formatInstant(value, locale) {
  if (!value) return '—';
  try {
    const d = typeof value === 'string' ? new Date(value) : new Date(value);
    return d.toLocaleString(locale, { dateStyle: 'short', timeStyle: 'medium' });
  } catch {
    return String(value);
  }
}

export default function AdminDocumentsDeletedPage() {
  const { t, i18n } = useTranslation();
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';
  const qc = useQueryClient();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-documents-deleted', page],
    queryFn: () => listDeletedDocuments(page, 20),
  });

  const restoreMut = useMutation({
    mutationFn: (id) => restoreDocument(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-documents-deleted'] });
      qc.invalidateQueries({ queryKey: ['documents'] });
      qc.invalidateQueries({ queryKey: ['search'] });
    },
  });

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <Link className="text-sm text-brand-mid hover:underline" to="/admin">
        ← {t('admin.backHub')}
      </Link>
      <h1 className="text-2xl font-semibold text-brand-dark mt-2 mb-2">{t('admin.deletedDocsTitle')}</h1>
      <p className="text-slate-600 mb-6">{t('admin.deletedDocsSubtitle')}</p>

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
                  <th className="p-2">{t('upload.field.title')}</th>
                  <th className="p-2">{t('admin.deletedAt')}</th>
                  <th className="p-2 w-40">{t('admin.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {content.map((row) => (
                  <tr key={row.id} className="border-t border-slate-100">
                    <td className="p-2 font-mono text-xs">{row.id}</td>
                    <td className="p-2">{row.title}</td>
                    <td className="p-2 text-slate-600 whitespace-nowrap">
                      {formatInstant(row.deletedAt, locale)}
                    </td>
                    <td className="p-2">
                      <button
                        type="button"
                        className="rounded bg-brand-mid px-2 py-1 text-xs text-white hover:opacity-90 disabled:opacity-50"
                        disabled={restoreMut.isPending}
                        onClick={() => {
                          if (window.confirm(t('admin.confirmRestore'))) {
                            restoreMut.mutate(row.id);
                          }
                        }}
                      >
                        {t('admin.restore')}
                      </button>
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={4} className="p-6 text-center text-slate-500">
                      {t('admin.deletedDocsEmpty')}
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
