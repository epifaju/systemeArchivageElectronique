import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { useTranslation } from 'react-i18next';
import { fetchDocumentTypes } from '../api/metadataApi';
import { uploadDocument, importBatch, importZip } from '../api/documentsApi';
import CustomMetadataFields, { buildCustomPayload, getSchemaFields } from '../components/CustomMetadataFields.jsx';

const LANGS = ['FRENCH', 'PORTUGUESE', 'OTHER', 'MULTILINGUAL'];
const CONF = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET'];

function formatDocDate(isoDate, locale) {
  if (!isoDate) return '—';
  try {
    const [y, m, d] = isoDate.split('-').map(Number);
    return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(y, m - 1, d));
  } catch {
    return isoDate;
  }
}

/** MIME parfois absent (ex. certains chemins Windows) : on se rabat sur l’extension. */
function isPdfFile(file) {
  if (!file) return false;
  const mime = (file.type || '').toLowerCase();
  if (mime === 'application/pdf' || mime === 'application/x-pdf') return true;
  return Boolean(file.name?.toLowerCase().endsWith('.pdf'));
}

/** Aligné sur DocumentUploadService.ALLOWED_MIMES (+ ZIP côté UI). */
const ALLOWED_EXT = new Set(['.pdf', '.jpg', '.jpeg', '.png', '.tif', '.tiff']);
const ALLOWED_MIME = new Set([
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/tiff',
  'image/tif',
]);

function extOf(file) {
  const n = (file.name || '').toLowerCase();
  const i = n.lastIndexOf('.');
  return i >= 0 ? n.slice(i) : '';
}

function isZipFile(file) {
  return Boolean(file?.name?.toLowerCase().endsWith('.zip'));
}

/** PDF / images acceptées par le serveur (pas Word, pas Excel, etc.). */
function isServerSupportedMimeOrExt(file) {
  if (!file) return false;
  if (ALLOWED_EXT.has(extOf(file))) return true;
  const mime = (file.type || '').toLowerCase();
  if (mime && ALLOWED_MIME.has(mime)) return true;
  return false;
}

function isUploadableFile(file) {
  if (isZipFile(file)) return true;
  return isServerSupportedMimeOrExt(file);
}

function canProceedToPreview(files) {
  if (!files.length) return false;
  const hasZip = files.some((f) => isZipFile(f));
  if (files.length > 1 && hasZip) return false;
  return files.every(isUploadableFile);
}

export default function UploadPage() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const langUi = i18n.language?.startsWith('pt') ? 'pt' : 'fr';
  const locale = i18n.language?.startsWith('pt') ? 'pt-PT' : 'fr-FR';

  const { data: types = [], isLoading: typesLoading } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
  });

  const [step, setStep] = useState('edit');

  const [title, setTitle] = useState('');
  const [documentTypeId, setDocumentTypeId] = useState('');
  const [folderNumber, setFolderNumber] = useState('');
  const [documentDate, setDocumentDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [language, setLanguage] = useState('FRENCH');
  const [confidentialityLevel, setConfidentialityLevel] = useState('INTERNAL');
  const [tagsInput, setTagsInput] = useState('');
  const [externalReference, setExternalReference] = useState('');
  const [author, setAuthor] = useState('');
  const [notes, setNotes] = useState('');
  const [files, setFiles] = useState([]);
  const [dropError, setDropError] = useState(null);
  const [imagePreviewFailed, setImagePreviewFailed] = useState(false);
  const [customMeta, setCustomMeta] = useState({});

  const selectedType = useMemo(
    () => types.find((x) => String(x.id) === String(documentTypeId)),
    [types, documentTypeId],
  );

  useEffect(() => {
    setCustomMeta({});
  }, [documentTypeId]);

  useEffect(() => {
    if (files.length === 0) setStep('edit');
  }, [files.length]);

  const metadata = useMemo(() => {
    const tags = tagsInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const schemaFields = getSchemaFields(selectedType?.customFieldsSchema);
    const customMetadata = buildCustomPayload(schemaFields, customMeta);
    return {
      title: title.trim(),
      documentTypeId: Number(documentTypeId),
      folderNumber: folderNumber.trim(),
      documentDate,
      language,
      confidentialityLevel,
      departmentId: null,
      externalReference: externalReference.trim() || null,
      author: author.trim() || null,
      notes: notes.trim() || null,
      tags: tags.length ? tags : null,
      customMetadata,
    };
  }, [
    title,
    documentTypeId,
    folderNumber,
    documentDate,
    language,
    confidentialityLevel,
    externalReference,
    author,
    notes,
    tagsInput,
    selectedType,
    customMeta,
  ]);

  const valid = title.trim() && documentTypeId && folderNumber.trim() && documentDate;

  const singleFile = files.length === 1 ? files[0] : null;

  useEffect(() => {
    setImagePreviewFailed(false);
  }, [singleFile?.name, singleFile?.type]);

  const imagePreviewUrl = useMemo(() => {
    if (!singleFile?.type?.startsWith('image/')) return null;
    return URL.createObjectURL(singleFile);
  }, [singleFile]);

  const pdfPreviewUrl = useMemo(() => {
    if (!singleFile || !isPdfFile(singleFile)) return null;
    return URL.createObjectURL(singleFile);
  }, [singleFile]);

  useEffect(() => {
    return () => {
      if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
      if (pdfPreviewUrl) URL.revokeObjectURL(pdfPreviewUrl);
    };
  }, [imagePreviewUrl, pdfPreviewUrl]);

  const onDrop = useCallback(
    (acceptedFiles) => {
      if (!acceptedFiles.length) return;
      const hasZip = acceptedFiles.some((f) => isZipFile(f));
      if (acceptedFiles.length > 1 && hasZip) {
        setDropError(t('upload.zipMixedError'));
        return;
      }
      const invalid = acceptedFiles.filter((f) => !isUploadableFile(f));
      if (invalid.length) {
        setDropError(t('upload.dropInvalidType', { name: invalid[0].name }));
        return;
      }
      setFiles(acceptedFiles);
      setDropError(null);
    },
    [t]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    disabled: step === 'preview',
    accept: {
      'application/pdf': ['.pdf'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
      'image/tiff': ['.tif', '.tiff'],
      'application/zip': ['.zip'],
      'application/x-zip-compressed': ['.zip'],
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!files.length) throw new Error('no files');
      const hasZip = files.some((f) => f.name.toLowerCase().endsWith('.zip'));
      if (files.length > 1 && hasZip) {
        throw new Error('ZIP_MIXED');
      }
      if (files.length === 1 && files[0].name.toLowerCase().endsWith('.zip')) {
        return importZip(files[0], metadata);
      }
      if (files.length === 1) {
        return uploadDocument(files[0], metadata);
      }
      return importBatch(files, metadata);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search'] });
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['home-dashboard'] });
      setFiles([]);
      setStep('edit');
    },
  });

  useEffect(() => {
    if (files.length > 0) {
      uploadMutation.reset();
    }
  }, [files, uploadMutation]);

  function onConfirmUpload() {
    if (!valid || !files.length) return;
    uploadMutation.mutate();
  }

  const typeLabel = selectedType
    ? langUi === 'pt'
      ? selectedType.labelPt
      : selectedType.labelFr
    : '—';

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('upload.title')}</h1>
      <p className="text-slate-600 mb-2">{t('upload.subtitle')}</p>
      <p className="text-xs text-slate-500 mb-6">{t('upload.formatsHint')}</p>

      <form
        className="space-y-6"
        onSubmit={(e) => {
          e.preventDefault();
        }}
      >
        {step === 'edit' && (
          <>
            <div
              {...getRootProps()}
              className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
                isDragActive ? 'border-brand-mid bg-blue-50' : 'border-slate-300 hover:border-brand-mid'
              }`}
            >
              <input {...getInputProps()} />
              <p className="text-slate-700">{t('upload.dropzone')}</p>
              {dropError && (
                <p className="mt-3 text-sm text-red-600" role="alert">
                  {dropError}
                </p>
              )}
              {files.length > 0 && (
                <p className="mt-2 text-xs text-slate-500">
                  {t('upload.selectedCount', { count: files.length })}
                </p>
              )}
              {files.length > 0 && (
                <ul className="mt-3 text-sm text-left max-h-40 overflow-auto space-y-1">
                  {files.map((f, i) => (
                    <li
                      key={`${f.name}-${i}`}
                      className="flex items-center justify-between gap-2 rounded border border-slate-100 bg-slate-50 px-2 py-1"
                    >
                      <span className="truncate" title={f.name}>
                        {f.name}
                      </span>
                      <button
                        type="button"
                        className="shrink-0 text-slate-500 hover:text-red-600 text-xs"
                        onClick={(e) => {
                          e.stopPropagation();
                          setFiles((prev) => prev.filter((_, j) => j !== i));
                          setDropError(null);
                        }}
                      >
                        {t('upload.removeFile')}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {files.length === 1 && files[0].name.toLowerCase().endsWith('.zip') && (
              <div className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-950">
                <p className="font-medium">{t('upload.zipMode')}</p>
                <p className="mt-1 text-sky-900/90">{t('upload.zipHint')}</p>
              </div>
            )}

            {files.length > 1 && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
                <p className="font-medium">{t('upload.batchMode')}</p>
                <p className="mt-1 text-amber-900/90">{t('upload.batchHint')}</p>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <span className="text-sm text-slate-600">{t('upload.field.title')}</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
                {files.length > 1 && (
                  <p className="mt-1 text-xs text-slate-500">{t('upload.titleBatchHint')}</p>
                )}
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.type')}</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={documentTypeId}
                  onChange={(e) => setDocumentTypeId(e.target.value)}
                  required
                  disabled={typesLoading}
                >
                  <option value="">{typesLoading ? '…' : '—'}</option>
                  {types.map((dt) => (
                    <option key={dt.id} value={dt.id}>
                      {langUi === 'pt' ? dt.labelPt : dt.labelFr}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.folder')}</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={folderNumber}
                  onChange={(e) => setFolderNumber(e.target.value)}
                  required
                />
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.date')}</span>
                <input
                  type="date"
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={documentDate}
                  onChange={(e) => setDocumentDate(e.target.value)}
                  required
                />
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.language')}</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                >
                  {LANGS.map((l) => (
                    <option key={l} value={l}>
                      {t(`enums.language.${l}`)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block sm:col-span-2">
                <span className="text-sm text-slate-600">{t('upload.field.confidentiality')}</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={confidentialityLevel}
                  onChange={(e) => setConfidentialityLevel(e.target.value)}
                >
                  {CONF.map((c) => (
                    <option key={c} value={c}>
                      {t(`enums.confidentiality.${c}`)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block sm:col-span-2">
                <span className="text-sm text-slate-600">{t('upload.field.tags')}</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={tagsInput}
                  onChange={(e) => setTagsInput(e.target.value)}
                  placeholder={t('upload.tagsHint')}
                />
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.externalRef')}</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={externalReference}
                  onChange={(e) => setExternalReference(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-sm text-slate-600">{t('upload.field.author')}</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={author}
                  onChange={(e) => setAuthor(e.target.value)}
                />
              </label>
              <label className="block sm:col-span-2">
                <span className="text-sm text-slate-600">{t('upload.field.notes')}</span>
                <textarea
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  rows={2}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </label>
              <div className="sm:col-span-2">
                <CustomMetadataFields
                  schema={selectedType?.customFieldsSchema}
                  value={customMeta}
                  onChange={setCustomMeta}
                  lang={langUi}
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                disabled={!valid || !files.length || !canProceedToPreview(files)}
                className="rounded bg-brand-mid px-4 py-2 text-white hover:bg-brand-dark disabled:opacity-50"
                onClick={() => setStep('preview')}
              >
                {t('upload.continueToPreview')}
              </button>
            </div>
          </>
        )}

        {step === 'preview' && (
          <div className="space-y-6 rounded-xl border border-slate-200 bg-slate-50/80 p-6">
            <div>
              <h2 className="text-lg font-semibold text-brand-dark">{t('upload.previewTitle')}</h2>
              <p className="text-sm text-slate-600 mt-1">{t('upload.previewSubtitle')}</p>
            </div>

            <div>
              <h3 className="text-sm font-medium text-slate-800 mb-2">{t('upload.previewFiles')}</h3>
              <ul className="text-sm space-y-1 list-disc list-inside text-slate-700">
                {files.map((f, i) => (
                  <li key={`${f.name}-${i}`} className="truncate" title={f.name}>
                    {f.name}
                    <span className="text-slate-500"> ({f.type || 'application/octet-stream'})</span>
                  </li>
                ))}
              </ul>

              {files.length === 1 && singleFile && isZipFile(singleFile) && (
                <div className="mt-4 rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-950">
                  {t('upload.previewZipNote')}
                </div>
              )}

              {files.length > 1 && (
                <p className="mt-4 text-sm text-slate-600">{t('upload.previewMultiHint')}</p>
              )}

              {files.length === 1 && singleFile && !isZipFile(singleFile) && imagePreviewUrl && !imagePreviewFailed && (
                <div className="mt-4 flex justify-center">
                  <img
                    src={imagePreviewUrl}
                    alt=""
                    onError={() => setImagePreviewFailed(true)}
                    className="max-h-64 max-w-full rounded border border-slate-200 shadow-sm object-contain"
                  />
                </div>
              )}
              {files.length === 1 && singleFile && !isZipFile(singleFile) && imagePreviewUrl && imagePreviewFailed && (
                <p className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
                  {t('upload.previewImageFailed')}
                </p>
              )}
              {pdfPreviewUrl && (
                <div className="mt-4 w-full">
                  <iframe
                    title={singleFile.name}
                    src={`${pdfPreviewUrl}#toolbar=1`}
                    className="h-[min(70vh,560px)] w-full rounded border border-slate-200 bg-slate-100 shadow-sm"
                  />
                  <p className="mt-2 text-xs text-slate-500">{t('upload.previewPdfHint')}</p>
                </div>
              )}
            </div>

            <dl className="grid gap-2 sm:grid-cols-2 text-sm border-t border-slate-200 pt-4">
              <dt className="text-slate-500">{t('upload.field.title')}</dt>
              <dd className="text-slate-900 font-medium">{metadata.title}</dd>
              <dt className="text-slate-500">{t('upload.field.type')}</dt>
              <dd className="text-slate-900">{typeLabel}</dd>
              <dt className="text-slate-500">{t('upload.field.folder')}</dt>
              <dd className="text-slate-900">{metadata.folderNumber}</dd>
              <dt className="text-slate-500">{t('upload.field.date')}</dt>
              <dd className="text-slate-900">{formatDocDate(metadata.documentDate, locale)}</dd>
              <dt className="text-slate-500">{t('upload.field.language')}</dt>
              <dd className="text-slate-900">{t(`enums.language.${metadata.language}`)}</dd>
              <dt className="text-slate-500">{t('upload.field.confidentiality')}</dt>
              <dd className="text-slate-900">{t(`enums.confidentiality.${metadata.confidentialityLevel}`)}</dd>
              {metadata.tags && metadata.tags.length > 0 && (
                <>
                  <dt className="text-slate-500">{t('upload.field.tags')}</dt>
                  <dd className="text-slate-900">{metadata.tags.join(', ')}</dd>
                </>
              )}
              {metadata.externalReference && (
                <>
                  <dt className="text-slate-500">{t('upload.field.externalRef')}</dt>
                  <dd className="text-slate-900">{metadata.externalReference}</dd>
                </>
              )}
              {metadata.author && (
                <>
                  <dt className="text-slate-500">{t('upload.field.author')}</dt>
                  <dd className="text-slate-900">{metadata.author}</dd>
                </>
              )}
              {metadata.notes && (
                <>
                  <dt className="text-slate-500 sm:col-span-2">{t('upload.field.notes')}</dt>
                  <dd className="text-slate-900 sm:col-span-2 whitespace-pre-wrap">{metadata.notes}</dd>
                </>
              )}
            </dl>

            <div className="flex flex-wrap gap-3 pt-2">
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-4 py-2 text-slate-800 hover:bg-slate-50"
                onClick={() => setStep('edit')}
              >
                {t('upload.backToEdit')}
              </button>
              <button
                type="button"
                disabled={uploadMutation.isPending || !canProceedToPreview(files)}
                className="rounded bg-brand-mid px-4 py-2 text-white hover:bg-brand-dark disabled:opacity-50"
                onClick={onConfirmUpload}
              >
                {uploadMutation.isPending
                  ? t('upload.sending')
                  : files.length === 1 && files[0].name.toLowerCase().endsWith('.zip')
                    ? t('upload.submitZip')
                    : files.length > 1
                      ? t('upload.submitBatch', { count: files.length })
                      : t('upload.confirmSend')}
              </button>
            </div>
          </div>
        )}

        {uploadMutation.isError && (
          <p className="text-sm text-red-600">
            {uploadMutation.error?.message === 'ZIP_MIXED'
              ? t('upload.zipMixedError')
              : uploadMutation.error?.response?.status === 409 ||
                  uploadMutation.error?.response?.data?.code === 'DUPLICATE_DOCUMENT'
                ? t('upload.duplicateError')
                : uploadMutation.error?.response?.data?.message || uploadMutation.error?.message || t('upload.error')}
          </p>
        )}
        {uploadMutation.isSuccess && uploadMutation.data && (
          <p className="text-sm text-green-700">
            {Array.isArray(uploadMutation.data)
              ? t('upload.successBatch', { count: uploadMutation.data.length })
              : t('upload.success')}
          </p>
        )}
      </form>
    </div>
  );
}
