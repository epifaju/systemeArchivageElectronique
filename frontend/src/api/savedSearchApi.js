import api from './axiosInstance.js';

export async function fetchSavedSearches() {
  const { data } = await api.get('/api/saved-searches');
  return data;
}

export async function createSavedSearch(body) {
  const { data } = await api.post('/api/saved-searches', body);
  return data;
}

export async function deleteSavedSearch(id) {
  await api.delete(`/api/saved-searches/${id}`);
}

export async function updateSavedSearch(id, body) {
  const { data } = await api.put(`/api/saved-searches/${id}`, body);
  return data;
}
