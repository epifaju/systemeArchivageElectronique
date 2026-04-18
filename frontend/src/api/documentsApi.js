import api from './axiosInstance.js';

/**
 * @param {File} file
 * @param {object} metadata — champs alignés sur UploadRequest backend
 */
export async function uploadDocument(file, metadata) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  const { data } = await api.post('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export async function importBatch(files, metadata) {
  const formData = new FormData();
  files.forEach((f) => formData.append('files', f));
  formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  const { data } = await api.post('/api/documents/import-batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

/**
 * @param {File} zipFile
 * @param {object} metadata — modèle appliqué à chaque fichier extrait (titres « préfixe — nom »)
 */
export async function importZip(zipFile, metadata) {
  const formData = new FormData();
  formData.append('zip', zipFile);
  formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  const { data } = await api.post('/api/documents/import-zip', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}
