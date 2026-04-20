import { useEffect } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { performLogout } from '../auth/performLogout';
import { useAuthStore } from '../store/authStore';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

function navLinkClass({ isActive }) {
  return [
    'rounded-md px-2 py-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2',
    isActive ? 'text-brand-dark bg-slate-100' : 'text-slate-700 hover:text-brand-mid hover:bg-slate-50',
  ].join(' ');
}

export default function AppLayout({ children }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const clear = useAuthStore((s) => s.clear);
  const role = useEffectiveRole();
  const isAdmin = role === 'ADMIN';
  const canOcrQueue = role === 'ADMIN' || role === 'ARCHIVISTE';
  const canAuditLog = role === 'ADMIN' || role === 'AUDITEUR';
  const canBrowseDocuments = role && role !== 'AUDITEUR';

  async function logout() {
    await performLogout(clear, navigate);
  }

  const rawIdle = import.meta.env.VITE_IDLE_TIMEOUT_MINUTES;
  const idleMinutes =
    rawIdle === undefined || rawIdle === '' ? 30 : Number(rawIdle);
  const idleMs =
    Number.isFinite(idleMinutes) && idleMinutes > 0 ? idleMinutes * 60 * 1000 : 0;

  useEffect(() => {
    if (!idleMs) return undefined;
    let timer;
    function arm() {
      clearTimeout(timer);
      timer = setTimeout(() => {
        performLogout(clear, navigate);
      }, idleMs);
    }
    function onActivity() {
      if (document.visibilityState === 'hidden') return;
      arm();
    }
    const events = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'scroll', 'wheel', 'visibilitychange'];
    arm();
    events.forEach((ev) => window.addEventListener(ev, onActivity, { passive: true }));
    return () => {
      clearTimeout(timer);
      events.forEach((ev) => window.removeEventListener(ev, onActivity));
    };
  }, [idleMs, clear, navigate]);

  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur supports-[backdrop-filter]:bg-white/70">
        <div className="px-6 py-3 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <Link
              to="/dashboard"
              className="font-semibold text-brand-dark truncate hover:text-brand-mid focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2 rounded"
              title={t('app.title')}
            >
              {t('app.title')}
            </Link>
            {role && (
              <span className="hidden sm:inline-flex text-xs font-medium rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-slate-700">
                {t(`admin.roles.${role}`)}
              </span>
            )}
          </div>

          <nav className="flex flex-wrap items-center justify-end gap-1 text-sm">
            <NavLink className={navLinkClass} to="/dashboard">
              {t('nav.dashboard')}
            </NavLink>

            {canBrowseDocuments && (
              <>
                <div className="mx-1 hidden sm:block h-5 w-px bg-slate-200" aria-hidden="true" />
                <NavLink className={navLinkClass} to="/upload">
                  {t('nav.upload')}
                </NavLink>
                <NavLink className={navLinkClass} to="/search">
                  {t('nav.search')}
                </NavLink>
                <NavLink className={navLinkClass} to="/documents">
                  {t('nav.documents')}
                </NavLink>
              </>
            )}

            {(isAdmin || canOcrQueue || canAuditLog) && (
              <>
                <div className="mx-1 hidden sm:block h-5 w-px bg-slate-200" aria-hidden="true" />
                {isAdmin && (
                  <NavLink className={navLinkClass} to="/admin">
                    {t('nav.admin')}
                  </NavLink>
                )}
                {canOcrQueue && (
                  <NavLink className={navLinkClass} to="/admin/ocr-queue">
                    {t('nav.ocrQueue')}
                  </NavLink>
                )}
                {canAuditLog && (
                  <NavLink className={navLinkClass} to="/admin/audit">
                    {t('nav.auditLog')}
                  </NavLink>
                )}
              </>
            )}

            <div className="mx-1 hidden sm:block h-5 w-px bg-slate-200" aria-hidden="true" />
            <div className="flex items-center gap-1 text-xs text-slate-600" role="group" aria-label={t('app.languageGroup')}>
              <button
                type="button"
                className={`rounded px-1.5 py-0.5 ${i18n.language?.startsWith('fr') ? 'bg-slate-100 font-medium text-brand-dark' : 'hover:bg-slate-50'}`}
                onClick={() => i18n.changeLanguage('fr')}
              >
                FR
              </button>
              <button
                type="button"
                className={`rounded px-1.5 py-0.5 ${i18n.language?.startsWith('pt') ? 'bg-slate-100 font-medium text-brand-dark' : 'hover:bg-slate-50'}`}
                onClick={() => i18n.changeLanguage('pt')}
              >
                PT
              </button>
            </div>
            <div className="mx-1 hidden sm:block h-5 w-px bg-slate-200" aria-hidden="true" />
            <button
              type="button"
              className="rounded-md px-2 py-1 text-slate-600 hover:text-brand-mid hover:bg-slate-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-mid focus-visible:ring-offset-2"
              onClick={logout}
            >
              {t('auth.logout')}
            </button>
          </nav>
        </div>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
