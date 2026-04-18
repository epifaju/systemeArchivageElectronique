import api from './axiosInstance.js';

export async function fetchDocumentTypes() {
  const { data } = await api.get('/api/metadata/document-types');
  return data;
}
