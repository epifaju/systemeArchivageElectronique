import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { useTranslation } from 'react-i18next';
import { fetchDocumentTypes } from '../api/metadataApi';
import { uploadDocument, importBatch } from '../api/documentsApi';

const LANGS = ['FRENCH', 'PORTUGUESE', 'OTHER', 'MULTILINGUAL'];
const CONF = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'SECRET'];

export default function UploadPage() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const langUi = i18n.language?.startsWith('pt') ? 'pt' : 'fr';

  const { data: types = [], isLoading: typesLoading } = useQuery({
    queryKey: ['document-types'],
    queryFn: fetchDocumentTypes,
  });

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

  const metadata = useMemo(() => {
    const tags = tagsInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
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
  ]);

  const valid = title.trim() && documentTypeId && folderNumber.trim() && documentDate;

  const onDrop = useCallback((accepted) => {
    setFiles(accepted);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    disabled: false,
  });

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!files.length) throw new Error('no files');
      if (files.length === 1) {
        return uploadDocument(files[0], metadata);
      }
      return importBatch(files, metadata);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search'] });
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      setFiles([]);
    },
  });

  useEffect(() => {
    if (files.length > 0) {
      uploadMutation.reset();
    }
  }, [files, uploadMutation]);

  function onSubmit(e) {
    e.preventDefault();
    if (!valid || !files.length) return;
    uploadMutation.mutate();
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <h1 className="text-2xl font-semibold text-brand-dark mb-2">{t('upload.title')}</h1>
      <p className="text-slate-600 mb-6">{t('upload.subtitle')}</p>

      <form onSubmit={onSubmit} className="space-y-6">
        <div
          {...getRootProps()}
          className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
            isDragActive ? 'border-brand-mid bg-blue-50' : 'border-slate-300 hover:border-brand-mid'
          }`}
        >
          <input {...getInputProps()} />
          <p className="text-slate-700">{t('upload.dropzone')}</p>
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
                    }}
                  >
                    {t('upload.removeFile')}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

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
        </div>

        {uploadMutation.isError && (
          <p className="text-sm text-red-600">
            {uploadMutation.error?.response?.data?.message || uploadMutation.error?.message || t('upload.error')}
          </p>
        )}
        {uploadMutation.isSuccess && uploadMutation.data && (
          <p className="text-sm text-green-700">
            {Array.isArray(uploadMutation.data)
              ? t('upload.successBatch', { count: uploadMutation.data.length })
              : t('upload.success')}
          </p>
        )}

        <button
          type="submit"
          disabled={!valid || !files.length || uploadMutation.isPending}
          className="rounded bg-brand-mid px-4 py-2 text-white hover:bg-brand-dark disabled:opacity-50"
        >
          {uploadMutation.isPending
            ? t('upload.sending')
            : files.length > 1
              ? t('upload.submitBatch', { count: files.length })
              : t('upload.submit')}
        </button>
      </form>
    </div>
  );
}
