/** Décode le payload JWT (usage UI uniquement — pas de vérification de signature). */
export function getJwtPayload() {
  try {
    const t = localStorage.getItem('accessToken');
    if (!t) return null;
    const part = t.split('.')[1];
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '==='.slice((b64.length + 3) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

/** Aligné sur les noms d’énum côté API (ADMIN, …), sans préfixe Spring ROLE_. */
export function normalizeRole(role) {
  if (role == null || role === '') return null;
  const s = String(role);
  return s.startsWith('ROLE_') ? s.slice(5) : s;
}

/** Préférer `useEffectiveRole()` dans les composants pour tenir compte du profil persisté. */
export function getJwtRole() {
  return normalizeRole(getJwtPayload()?.role ?? null);
}
