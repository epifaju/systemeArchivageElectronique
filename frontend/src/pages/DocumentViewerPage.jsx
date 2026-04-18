import { useCallback, useEffect, useMemo, useState } from 'react';
import { Document, Page } from 'react-pdf';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  deleteDocument,
  fetchDocument,
  fetchDocumentHistory,
  fetchDocumentTypes,
  fetchPreviewArrayBuffer,
  fetchPreviewBlob,
  reprocessDocumentOcr,
  triggerBlobDownload,
  updateDocumentMetadata,
} from '../api/documentApi';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

function defaultShowOcrFirst(mime, ocrAvailable) {
  if (!ocrAvailable) return false;
  return mime === 'application/pdf';
}

function isPdfMagic(bytes) {
  if (!bytes || bytes.byteLength < 5) return false;
  return (
    bytes[0] === 0x25 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x44 &&
    bytes[3] === 0x46 &&
    bytes[4] === 0x2d
  );
}

/** PDF attendu : fichier OCR ou original PDF. */
function shouldExpectPdf(doc, previewPath) {
  if (!previewPath || !doc) return false;
  if (previewPath.includes('/preview/ocr')) return true;
  return doc.mimeType === 'application/pdf';
}

function parseJsonErrorMessage(bytes) {
  try {
    const txt = new TextDecoder().decode(bytes.slice(0, Math.min(bytes.byteLength, 4096)));
    const j = JSON.parse(txt);
    return j.message || null;
  } catch {
    return null;
  }
}

export default function DocumentViewerPage() {
  const { id } = useParams();
  const docId = Number(id);
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const role = useEffectiveRole();
  const canEdit = ['AGENT', 'ARCHIVISTE', 'ADMIN'].includes(role);
  const canReprocess = ['ARCHIVISTE', 'ADMIN'].includes(role);
  const canSoftDelete = ['ARCHIVISTE', 'ADMIN'].includes(role);

  const [showOcrPdf, setShowOcrPdf] = useState(false);
  /** ArrayBuffer — react-pdf n’accepte pas Uint8Array seul comme `file` (sinon échec silencieux). */
  const [pdfBuffer, setPdfBuffer] = useState(null);
  const [imageObjectUrl, setImageObjectUrl] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(true);
  const [previewError, setPreviewError] = useState(null);
  const [pdfRenderError, setPdfRenderError] = useState(null);
  const [pdfPage, setPdfPage] = useState(1);
  const [pdfPages, setPdfPages] = useState(0);
  const [pdfScale, setPdfScale] = useState(1);
  const [panelWidth, setPanelWidth] = useState(720);
  const [editing, setEditing] = useState(false);
  const [imgError, setImgError] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyIncludeViews, setHistoryIncludeViews] = useState(false);

  const { data: doc, isLoading, isError, error } = useQuery({
    queryKey: ['document', docId],
    queryFn: () => fetchDocument(docId),
    enabled: Number.isFinite(docId),
  });

  const { data: docTypes = [] } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
    enabled: editing && canEdit,
  });

  const historyPageSize = 15;
  const {
    data: historyData,
    isLoading: historyLoading,
    isError: historyError,
    error: historyErr,
  } = useQuery({
    queryKey: ['document-history', docId, historyPage, historyIncludeViews],
    queryFn: () =>
      fetchDocumentHistory(docId, {
        page: historyPage,
        size: historyPageSize,
        includeViews: historyIncludeViews,
      }),
    enabled: Number.isFinite(docId) && !!doc,
  });

  useEffect(() => {
    function onResize() {
      setPanelWidth(Math.min(920, Math.max(320, window.innerWidth - 120)));
    }
    onResize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (!doc) return;
    setShowOcrPdf(defaultShowOcrFirst(doc.mimeType, doc.ocrAvailable));
    setPdfPage(1);
    setImgError(false);
  }, [doc?.id, doc?.mimeType, doc?.ocrAvailable]);

  const previewPath = useMemo(() => {
    if (!doc) return null;
    const useOcr = showOcrPdf && doc.ocrAvailable;
    return useOcr ? `/api/documents/${docId}/preview/ocr` : `/api/documents/${docId}/preview`;
  }, [doc, docId, showOcrPdf]);

  useEffect(() => {
    if (!previewPath || !doc) {
      setPreviewLoading(false);
      return undefined;
    }
    let cancelled = false;

    setPreviewLoading(true);
    setPreviewError(null);
    setPdfRenderError(null);
    setPdfBuffer(null);
    setImageObjectUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return null;
    });

    (async () => {
      try {
        const ab = await fetchPreviewArrayBuffer(previewPath);
        if (cancelled) return;
        const expectPdf = shouldExpectPdf(doc, previewPath);
        const head = new Uint8Array(ab, 0, Math.min(5, ab.byteLength));

        if (expectPdf) {
          if (!isPdfMagic(head)) {
            const apiMsg = parseJsonErrorMessage(new Uint8Array(ab));
            setPreviewError(apiMsg || t('viewer.previewInvalidPdf'));
            return;
          }
          setPdfBuffer(ab);
          return;
        }

        const bytes = new Uint8Array(ab);
        const blob = new Blob([bytes], { type: doc.mimeType || 'application/octet-stream' });
        const url = URL.createObjectURL(blob);
        setImageObjectUrl(url);
      } catch (e) {
        if (!cancelled) {
          const status = e?.response?.status;
          let msg =
            e?.response?.data?.message ||
            (status === 404 ? t('viewer.preview404') : null) ||
            e?.message ||
            t('viewer.previewLoadFailed');
          if (typeof e?.response?.data === 'string' && e.response.data.length < 500) {
            try {
              const j = JSON.parse(e.response.data);
              if (j?.message) msg = j.message;
            } catch {
              /* ignore */
            }
          }
          setPreviewError(msg);
        }
      } finally {
        if (!cancelled) setPreviewLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      setPdfBuffer(null);
      setImageObjectUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
    };
  }, [previewPath, doc, t]);

  const typeLabel = useMemo(() => {
    if (!doc) return '';
    return i18n.language?.startsWith('pt') ? doc.documentTypeLabelPt : doc.documentTypeLabelFr;
  }, [doc, i18n.language]);

  const metaMutation = useMutation({
    mutationFn: (body) => updateDocumentMetadata(docId, body),
    onSuccess: (d) => {
      qc.setQueryData(['document', docId], d);
      setEditing(false);
      qc.invalidateQueries({ queryKey: ['document-history', docId] });
    },
  });

  const ocrMutation = useMutation({
    mutationFn: () => reprocessDocumentOcr(docId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document', docId] });
      qc.invalidateQueries({ queryKey: ['document-history', docId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteDocument(docId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents'] });
      qc.invalidateQueries({ queryKey: ['search'] });
      navigate('/documents');
    },
  });

  const downloadOriginal = useCallback(async () => {
    const blob = await fetchPreviewBlob(`/api/documents/${docId}/download/original`);
    const ext = guessExt(doc.mimeType, 'bin');
    triggerBlobDownload(blob, safeFilename(doc.title, ext));
  }, [docId, doc]);

  const downloadOcr = useCallback(async () => {
    const blob = await fetchPreviewBlob(`/api/documents/${docId}/download/ocr`);
    triggerBlobDownload(blob, safeFilename(doc.title, 'pdf'));
  }, [docId, doc]);

  if (!Number.isFinite(docId)) {
    return <p className="p-6 text-red-600">{t('viewer.badId')}</p>;
  }

  if (isLoading) {
    return <p className="p-6 text-slate-600">{t('viewer.loading')}</p>;
  }

  if (isError || !doc) {
    return (
      <p className="p-6 text-red-600">
        {error?.response?.data?.message || error?.message || t('viewer.loadError')}
      </p>
    );
  }

  const isPdfView =
    (showOcrPdf && doc.ocrAvailable) || (!showOcrPdf && doc.mimeType === 'application/pdf');
  const isImage =
    !showOcrPdf &&
    doc.mimeType?.startsWith('image/') &&
    doc.mimeType !== 'image/tiff' &&
    doc.mimeType !== 'image/tif';
  const isTiffView =
    !showOcrPdf && (doc.mimeType === 'image/tiff' || doc.mimeType === 'image/tif');

  return (
    <div className="flex flex-col min-h-[calc(100vh-3.5rem)]">
      <div className="border-b border-slate-200 bg-white px-4 py-3 flex flex-wrap items-center gap-3">
        <Link className="text-sm text-brand-mid hover:underline" to="/search">
          {t('viewer.backSearch')}
        </Link>
        <h1 className="text-lg font-semibold text-brand-dark flex-1 min-w-0 truncate">{doc.title}</h1>
        <button
          type="button"
          className="text-sm text-brand-mid hover:underline shrink-0"
          onClick={() => document.getElementById('document-history')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
        >
          {t('viewer.historyJump')}
        </button>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50"
            onClick={() => downloadOriginal()}
          >
            {t('viewer.downloadOriginal')}
          </button>
          {doc.ocrAvailable && (
            <button
              type="button"
              className="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50"
              onClick={() => downloadOcr()}
            >
              {t('viewer.downloadOcr')}
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-1 flex-col lg:flex-row lg:items-start">
        <div className="flex-1 min-w-0 bg-slate-100 p-4 overflow-auto flex flex-col items-center">
          {doc.ocrAvailable && (
            <div className="mb-3 flex flex-wrap gap-2 justify-center">
              {doc.mimeType === 'application/pdf' && (
                <>
                  <button
                    type="button"
                    className={`rounded px-3 py-1 text-sm ${showOcrPdf ? 'bg-brand-mid text-white' : 'bg-white border'}`}
                    onClick={() => setShowOcrPdf(true)}
                  >
                    {t('viewer.tabOcr')}
                  </button>
                  <button
                    type="button"
                    className={`rounded px-3 py-1 text-sm ${!showOcrPdf ? 'bg-brand-mid text-white' : 'bg-white border'}`}
                    onClick={() => setShowOcrPdf(false)}
                  >
                    {t('viewer.tabOriginal')}
                  </button>
                </>
              )}
              {doc.mimeType?.startsWith('image/') && (
                <>
                  <button
                    type="button"
                    className={`rounded px-3 py-1 text-sm ${!showOcrPdf ? 'bg-brand-mid text-white' : 'bg-white border'}`}
                    onClick={() => setShowOcrPdf(false)}
                  >
                    {t('viewer.tabImage')}
                  </button>
                  <button
                    type="button"
                    className={`rounded px-3 py-1 text-sm ${showOcrPdf ? 'bg-brand-mid text-white' : 'bg-white border'}`}
                    onClick={() => setShowOcrPdf(true)}
                  >
                    {t('viewer.tabOcrPdf')}
                  </button>
                </>
              )}
            </div>
          )}

          {previewError && (
            <p className="text-red-600 text-sm text-center max-w-lg">{previewError}</p>
          )}

          {previewLoading && !previewError && (
            <p className="text-slate-600">{t('viewer.previewLoading')}</p>
          )}

          {!previewLoading && !previewError && imageObjectUrl && isTiffView && (
            <p className="text-slate-600 max-w-lg text-center">{t('viewer.tiffHint')}</p>
          )}

          {!previewLoading && !previewError && imageObjectUrl && isImage && !showOcrPdf && (
            <div className="max-w-full">
              {!imgError ? (
                <img
                  src={imageObjectUrl}
                  alt=""
                  className="max-h-[70vh] w-auto max-w-full object-contain shadow"
                  onError={() => setImgError(true)}
                />
              ) : (
                <p className="text-slate-600">{t('viewer.imageError')}</p>
              )}
            </div>
          )}

          {!previewLoading && !previewError && pdfBuffer && isPdfView && (
            <div className="w-full max-w-full flex flex-col items-center">
              <div className="flex flex-wrap gap-2 items-center mb-2">
                <button
                  type="button"
                  className="rounded border px-2 py-1 text-sm"
                  disabled={pdfPage <= 1}
                  onClick={() => setPdfPage((p) => Math.max(1, p - 1))}
                >
                  {t('viewer.prev')}
                </button>
                <span className="text-sm text-slate-600">
                  {t('viewer.pageOf', { current: pdfPage, total: pdfPages || '…' })}
                </span>
                <button
                  type="button"
                  className="rounded border px-2 py-1 text-sm"
                  disabled={pdfPages > 0 && pdfPage >= pdfPages}
                  onClick={() => setPdfPage((p) => (pdfPages ? Math.min(pdfPages, p + 1) : p + 1))}
                >
                  {t('viewer.next')}
                </button>
                <button
                  type="button"
                  className="rounded border px-2 py-1 text-sm"
                  onClick={() => setPdfScale((s) => Math.min(2.5, s + 0.15))}
                >
                  +
                </button>
                <button
                  type="button"
                  className="rounded border px-2 py-1 text-sm"
                  onClick={() => setPdfScale((s) => Math.max(0.5, s - 0.15))}
                >
                  −
                </button>
              </div>
              {pdfRenderError && (
                <p className="text-sm text-red-600 mb-2 max-w-lg text-center">{pdfRenderError}</p>
              )}
              <Document
                key={previewPath}
                file={pdfBuffer}
                onLoadSuccess={({ numPages }) => {
                  setPdfRenderError(null);
                  setPdfPages(numPages);
                  setPdfPage(1);
                }}
                onLoadError={(err) => {
                  setPdfRenderError(err?.message || t('viewer.pdfError'));
                }}
                loading={<p className="text-sm text-slate-500">{t('viewer.pdfLoading')}</p>}
              >
                <Page
                  pageNumber={pdfPage}
                  width={panelWidth * pdfScale}
                  renderTextLayer
                  renderAnnotationLayer
                />
              </Document>
            </div>
          )}
        </div>

        <aside className="w-full lg:w-[380px] shrink-0 border-t lg:border-t-0 lg:border-l border-slate-200 bg-white p-4">
          {editing && canEdit ? (
            <MetadataEditor
              doc={doc}
              docTypes={docTypes}
              onCancel={() => setEditing(false)}
              onSave={(body) => metaMutation.mutate(body)}
              saving={metaMutation.isPending}
              error={metaMutation.error}
              t={t}
            />
          ) : (
            <>
              <div className="flex justify-between items-center mb-3">
                <h2 className="font-semibold text-brand-dark">{t('viewer.metadata')}</h2>
                {canEdit && (
                  <button
                    type="button"
                    className="text-sm text-brand-mid hover:underline"
                    onClick={() => setEditing(true)}
                  >
                    {t('viewer.edit')}
                  </button>
                )}
              </div>
              <dl className="space-y-2 text-sm">
                <Row label={t('upload.field.title')} value={doc.title} />
                <Row label={t('upload.field.type')} value={typeLabel || doc.documentTypeCode} />
                <Row label={t('upload.field.folder')} value={doc.folderNumber} />
                <Row label={t('upload.field.date')} value={doc.documentDate} />
                <Row label={t('upload.field.language')} value={t(`enums.language.${doc.language}`)} />
                <Row
                  label={t('upload.field.confidentiality')}
                  value={t(`enums.confidentiality.${doc.confidentialityLevel}`)}
                />
                <Row label={t('viewer.status')} value={doc.status} />
                <Row label={t('viewer.mime')} value={doc.mimeType} />
                <Row label={t('viewer.sha')} value={doc.sha256} mono />
                {doc.externalReference && (
                  <Row label={t('upload.field.externalRef')} value={doc.externalReference} />
                )}
                {doc.author && <Row label={t('upload.field.author')} value={doc.author} />}
                {doc.notes && <Row label={t('upload.field.notes')} value={doc.notes} />}
                {doc.tags?.length > 0 && (
                  <Row label={t('upload.field.tags')} value={doc.tags.join(', ')} />
                )}
              </dl>
              {canReprocess && (
                <button
                  type="button"
                  className="mt-4 w-full rounded border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 hover:bg-amber-100"
                  disabled={ocrMutation.isPending}
                  onClick={() => ocrMutation.mutate()}
                >
                  {t('viewer.reprocessOcr')}
                </button>
              )}
              {canSoftDelete && (
                <button
                  type="button"
                  className="mt-3 w-full rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900 hover:bg-red-100 disabled:opacity-50"
                  disabled={deleteMutation.isPending}
                  onClick={() => {
                    if (window.confirm(t('viewer.confirmSoftDelete'))) {
                      deleteMutation.mutate();
                    }
                  }}
                >
                  {t('viewer.softDelete')}
                </button>
              )}
            </>
          )}

          <div id="document-history" className="mt-6 pt-4 border-t border-slate-200 scroll-mt-4">
            <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
              <h2 className="font-semibold text-brand-dark">{t('viewer.history')}</h2>
              <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
                <input
                  type="checkbox"
                  checked={historyIncludeViews}
                  onChange={(e) => {
                    setHistoryIncludeViews(e.target.checked);
                    setHistoryPage(0);
                  }}
                />
                {t('viewer.historyIncludeViews')}
              </label>
            </div>
            {historyError && (
              <p className="text-sm text-red-600">
                {historyErr?.response?.data?.message || historyErr?.message || t('viewer.historyError')}
              </p>
            )}
            {historyLoading && <p className="text-sm text-slate-500">{t('viewer.historyLoading')}</p>}
            {!historyLoading && !historyError && historyData?.content?.length === 0 && (
              <p className="text-sm text-slate-500">{t('viewer.historyEmpty')}</p>
            )}
            {!historyLoading && !historyError && historyData?.content?.length > 0 && (
              <>
                <div className="overflow-x-auto max-h-72 overflow-y-auto rounded border border-slate-200">
                  <table className="min-w-full text-sm">
                    <thead className="bg-slate-50 text-left text-slate-600 sticky top-0">
                      <tr>
                        <th className="px-2 py-2 font-medium">{t('viewer.historyColDate')}</th>
                        <th className="px-2 py-2 font-medium">{t('viewer.historyColUser')}</th>
                        <th className="px-2 py-2 font-medium">{t('viewer.historyColAction')}</th>
                        <th className="px-2 py-2 font-medium">{t('viewer.historyColDetails')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {historyData.content.map((row) => (
                        <tr key={row.id} className="border-t border-slate-100">
                          <td className="px-2 py-1.5 whitespace-nowrap text-slate-700">
                            {formatHistoryDate(row.createdAt)}
                          </td>
                          <td className="px-2 py-1.5 text-slate-800">{row.username || '—'}</td>
                          <td className="px-2 py-1.5 font-mono text-xs text-slate-800">
                            {historyActionLabel(t, row.action)}
                          </td>
                          <td className="px-2 py-1.5 text-xs text-slate-600 max-w-[200px] lg:max-w-[240px]">
                            <span className="break-words">{formatHistoryDetails(row.details)}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {(historyData.totalPages > 1 || historyData.hasPrevious || historyData.hasNext) && (
                  <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
                    <button
                      type="button"
                      className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
                      disabled={historyPage <= 0}
                      onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                    >
                      {t('viewer.historyPrev')}
                    </button>
                    <span className="text-slate-600">
                      {t('viewer.historyPageOf', {
                        current: historyData.currentPage + 1,
                        total: historyData.totalPages,
                      })}
                    </span>
                    <button
                      type="button"
                      className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
                      disabled={!historyData.hasNext}
                      onClick={() => setHistoryPage((p) => p + 1)}
                    >
                      {t('viewer.historyNext')}
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}

function formatHistoryDate(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'short',
      timeStyle: 'medium',
    }).format(d);
  } catch {
    return String(iso);
  }
}

function formatHistoryDetails(details) {
  if (!details || typeof details !== 'object') return '—';
  const keys = Object.keys(details);
  if (keys.length === 0) return '—';
  try {
    const s = JSON.stringify(details);
    return s.length > 240 ? `${s.slice(0, 240)}…` : s;
  } catch {
    return '—';
  }
}

function historyActionLabel(t, action) {
  if (!action) return '—';
  const key = `viewer.historyAction.${action}`;
  const label = t(key);
  return label === key ? action : label;
}

function Row({ label, value, mono }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className={`text-slate-900 break-words ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}

function MetadataEditor({ doc, docTypes, onCancel, onSave, saving, error, t }) {
  const mergedTypes = useMemo(() => {
    if (!doc) return docTypes;
    if (docTypes.some((x) => x.id === doc.documentTypeId)) return docTypes;
    return [
      {
        id: doc.documentTypeId,
        code: doc.documentTypeCode,
        labelFr: doc.documentTypeLabelFr,
        labelPt: doc.documentTypeLabelPt,
      },
      ...docTypes,
    ];
  }, [doc, docTypes]);

  const [title, setTitle] = useState(doc.title);
  const [documentTypeId, setDocumentTypeId] = useState(String(doc.documentTypeId));
  const [folderNumber, setFolderNumber] = useState(doc.folderNumber);
  const [documentDate, setDocumentDate] = useState(doc.documentDate);
  const [language, setLanguage] = useState(doc.language);
  const [confidentialityLevel, setConfidentialityLevel] = useState(doc.confidentialityLevel);
  const [departmentId, setDepartmentId] = useState(doc.departmentId != null ? String(doc.departmentId) : '');
  const [externalReference, setExternalReference] = useState(doc.externalReference || '');
  const [author, setAuthor] = useState(doc.author || '');
  const [notes, setNotes] = useState(doc.notes || '');
  const [tags, setTags] = useState((doc.tags || []).join(', '));

  function submit(e) {
    e.preventDefault();
    const tagList = tags
      .split(',')
      .map((x) => x.trim())
      .filter(Boolean);
    onSave({
      title: title.trim(),
      documentTypeId: Number(documentTypeId),
      folderNumber: folderNumber.trim(),
      documentDate,
      language,
      confidentialityLevel,
      departmentId: departmentId ? Number(departmentId) : null,
      externalReference: externalReference.trim() || null,
      author: author.trim() || null,
      notes: notes.trim() || null,
      tags: tagList.length ? tagList : null,
    });
  }

  return (
    <form onSubmit={submit} className="space-y-3 text-sm">
      <h2 className="font-semibold text-brand-dark mb-2">{t('viewer.editMetadata')}</h2>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.title')}</span>
        <input
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.type')}</span>
        <select
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={documentTypeId}
          onChange={(e) => setDocumentTypeId(e.target.value)}
          required
        >
          {mergedTypes.map((dt) => (
            <option key={dt.id} value={dt.id}>
              {dt.code} — {dt.labelFr}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.folder')}</span>
        <input
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={folderNumber}
          onChange={(e) => setFolderNumber(e.target.value)}
          required
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.date')}</span>
        <input
          type="date"
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={documentDate || ''}
          onChange={(e) => setDocumentDate(e.target.value)}
          required
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.language')}</span>
        <select
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
        >
          {['FRENCH', 'PORTUGUESE', 'OTHER', 'MULTILINGUAL'].map((k) => (
            <option key={k} value={k}>
              {t(`enums.language.${k}`)}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.confidentiality')}</span>
        <select
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={confidentialityLevel}
          onChange={(e) => setConfidentialityLevel(e.target.value)}
        >
          {['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET'].map((k) => (
            <option key={k} value={k}>
              {t(`enums.confidentiality.${k}`)}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="text-slate-600">{t('viewer.departmentId')}</span>
        <input
          type="number"
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={departmentId}
          onChange={(e) => setDepartmentId(e.target.value)}
          placeholder="—"
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.externalRef')}</span>
        <input
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={externalReference}
          onChange={(e) => setExternalReference(e.target.value)}
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.author')}</span>
        <input
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={author}
          onChange={(e) => setAuthor(e.target.value)}
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.notes')}</span>
        <textarea
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          rows={3}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </label>
      <label className="block">
        <span className="text-slate-600">{t('upload.field.tags')}</span>
        <input
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
          value={tags}
          onChange={(e) => setTags(e.target.value)}
        />
      </label>
      {error && (
        <p className="text-red-600 text-xs">{error?.response?.data?.message || error?.message}</p>
      )}
      <div className="flex gap-2 pt-2">
        <button
          type="submit"
          className="rounded bg-brand-mid px-4 py-2 text-white text-sm disabled:opacity-50"
          disabled={saving}
        >
          {t('viewer.save')}
        </button>
        <button type="button" className="rounded border px-4 py-2 text-sm" onClick={onCancel}>
          {t('viewer.cancel')}
        </button>
      </div>
    </form>
  );
}

function safeFilename(title, ext) {
  const base = (title || 'document').replace(/[\\/:*?"<>|]/g, '_').slice(0, 120);
  return `${base}.${ext}`;
}

function guessExt(mime, fallback) {
  if (!mime) return fallback;
  const m = {
    'application/pdf': 'pdf',
    'image/jpeg': 'jpg',
    'image/png': 'png',
    'image/tiff': 'tif',
    'image/tif': 'tif',
  }[mime];
  return m || fallback;
}
