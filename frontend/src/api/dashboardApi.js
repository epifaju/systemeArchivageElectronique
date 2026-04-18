import api from './axiosInstance.js';

export async function fetchHomeDashboard() {
  const { data } = await api.get('/api/dashboard');
  return data;
}
