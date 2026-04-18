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
