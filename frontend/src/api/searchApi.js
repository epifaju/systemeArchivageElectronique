import api from './axiosInstance.js';

/**
 * Recherche (GET) — paramètres optionnels alignés sur SearchController
 */
export async function searchDocuments(params) {
  const { data } = await api.get('/api/search', { params });
  return data;
}

/**
 * Recherche avancée (POST)
 */
export async function searchAdvanced(body) {
  const { data } = await api.post('/api/search/advanced', body);
  return data;
}

/**
 * Export CSV (GET /api/search/export.csv) — mêmes filtres que la recherche, sans pagination.
 * @param {object} params — q, type, folderNumber, language, status, sort, max (défaut côté UI : 5000)
 * @returns {Promise<Blob>}
 */
export async function downloadSearchCsv(params) {
  try {
    const { data } = await api.get('/api/search/export.csv', {
      params,
      responseType: 'blob',
    });
    return data;
  } catch (e) {
    if (e.response?.data instanceof Blob) {
      const text = await e.response.data.text();
      try {
        const j = JSON.parse(text);
        throw new Error(j.message || text);
      } catch (inner) {
        if (inner instanceof SyntaxError) {
          throw new Error(text || e.message);
        }
        throw inner;
      }
    }
    throw e;
  }
}
