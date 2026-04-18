import { useAuthStore } from '../store/authStore';
import { getJwtPayload, normalizeRole } from '../utils/jwt';

function pickRoleFromUser(user) {
  if (!user) return null;
  const r = user.role;
  if (typeof r === 'string') return r;
  if (r && typeof r === 'object' && typeof r.name === 'string') return r.name;
  return null;
}

/**
 * Rôle effectif pour l’UI : profil session (persisté) puis claim JWT.
 * Évite un menu incomplet après rechargement si le store était vide avant hydratation.
 */
export function useEffectiveRole() {
  const userRole = useAuthStore((s) => pickRoleFromUser(s.user));
  const raw = userRole ?? getJwtPayload()?.role ?? null;
  return normalizeRole(raw);
}
