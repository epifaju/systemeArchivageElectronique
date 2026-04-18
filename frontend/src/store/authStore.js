import { create } from 'zustand';

/** Clé localStorage pour le profil (rôle) après connexion — utilisée aussi au bootstrap dans main.jsx */
export const USER_SUMMARY_KEY = 'userSummary';

export const useAuthStore = create((set) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  setSession: (payload) => {
    if (payload.user) {
      try {
        localStorage.setItem(USER_SUMMARY_KEY, JSON.stringify(payload.user));
      } catch {
        /* ignore */
      }
    }
    set({
      user: payload.user,
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
    });
  },
  clear: () => {
    localStorage.removeItem(USER_SUMMARY_KEY);
    set({ user: null, accessToken: null, refreshToken: null });
  },
}));
