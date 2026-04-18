import api from '../api/axiosInstance';

/**
 * Déconnexion serveur (révoque le refresh token) + nettoyage local.
 * @param {() => void} clear — ex. useAuthStore.getState().clear
 * @param {(path: string) => void} [navigate] — ex. react-router navigate ; si omis, pas de redirection
 */
export async function performLogout(clear, navigate) {
  const refresh = localStorage.getItem('refreshToken');
  try {
    if (refresh) {
      await api.post('/api/auth/logout', { refreshToken: refresh });
    }
  } catch {
    /* réseau / 401 : on nettoie quand même */
  }
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  clear();
  if (navigate) {
    navigate('/login');
  }
}
