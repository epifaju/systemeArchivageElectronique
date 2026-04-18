import api from './axiosInstance.js';

export async function listUsers(page = 0, size = 20) {
  const { data } = await api.get('/api/admin/users', { params: { page, size } });
  return data;
}

export async function createUser(body) {
  const { data } = await api.post('/api/admin/users', body);
  return data;
}

export async function updateUser(id, body) {
  const { data } = await api.put(`/api/admin/users/${id}`, body);
  return data;
}

export async function listDocumentTypes(page = 0, size = 50) {
  const { data } = await api.get('/api/admin/document-types', { params: { page, size } });
  return data;
}

export async function createDocumentType(body) {
  const { data } = await api.post('/api/admin/document-types', body);
  return data;
}

export async function getOcrQueueStats() {
  const { data } = await api.get('/api/admin/ocr-queue/stats');
  return data;
}

export async function listOcrQueue(page = 0, size = 20) {
  const { data } = await api.get('/api/admin/ocr-queue', { params: { page, size } });
  return data;
}

export async function listDeletedDocuments(page = 0, size = 20) {
  const { data } = await api.get('/api/admin/documents/deleted', { params: { page, size } });
  return data;
}

export async function restoreDocument(id) {
  await api.post(`/api/admin/documents/${id}/restore`);
}

export async function fetchSystemSettings() {
  const { data } = await api.get('/api/admin/system-settings');
  return data;
}

export async function listAuditLogs(page = 0, size = 50, filters = {}) {
  const params = { page, size, ...filters };
  Object.keys(params).forEach((k) => {
    if (params[k] === '' || params[k] === undefined || params[k] === null) {
      delete params[k];
    }
  });
  const { data } = await api.get('/api/admin/audit-logs', { params });
  return data;
}
