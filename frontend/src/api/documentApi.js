import api from './axiosInstance';

/** Liste paginée (GET /api/documents) — filtres optionnels alignés sur le backend */
export async function listDocuments(params) {
  const { data } = await api.get('/api/documents', { params });
  return data;
}

export async function fetchDocument(id) {
  const { data } = await api.get(`/api/documents/${id}`);
  return data;
}

/** Historique d’audit pour la fiche document (pagination). */
export async function fetchDocumentHistory(id, params = {}) {
  const { data } = await api.get(`/api/documents/${id}/history`, { params });
  return data;
}

export async function deleteDocument(id) {
  await api.delete(`/api/documents/${id}`);
}

export async function fetchPreviewBlob(relativePath) {
  const { data } = await api.get(relativePath, { responseType: 'blob' });
  return data;
}

/** Binaire brut — préféré pour react-pdf (évite les soucis de blob URL / StrictMode). */
export async function fetchPreviewArrayBuffer(relativePath) {
  const { data } = await api.get(relativePath, { responseType: 'arraybuffer' });
  return data;
}

export async function updateDocumentMetadata(id, body) {
  const { data } = await api.put(`/api/documents/${id}/metadata`, body);
  return data;
}

/** Mise à jour partielle (PATCH) — champs absents = inchangés côté serveur. */
export async function patchDocumentMetadata(id, body) {
  const { data } = await api.patch(`/api/documents/${id}/metadata`, body);
  return data;
}

/** Suggestions heuristiques (dates, références, e-mails) depuis le texte OCR. */
export async function fetchMetadataSuggestions(id) {
  const { data } = await api.get(`/api/documents/${id}/metadata-suggestions`);
  return data;
}

export async function reprocessDocumentOcr(id) {
  await api.post(`/api/documents/${id}/reprocess-ocr`);
}

/** Changement de statut (ARCHIVISTE, ADMIN). */
export async function updateDocumentStatus(id, body) {
  const { data } = await api.put(`/api/documents/${id}/status`, body);
  return data;
}

export async function fetchDocumentTypes() {
  const { data } = await api.get('/api/metadata/document-types');
  return data;
}

export function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
