import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { performLogout } from '../auth/performLogout';
import { useAuthStore } from '../store/authStore';
import { useEffectiveRole } from '../hooks/useEffectiveRole';

export default function AppLayout({ children }) {
  const { t } = useTranslation();
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
      <header className="border-b border-slate-200 bg-white px-6 py-3 flex items-center justify-between gap-4">
        <span className="font-medium text-brand-dark">{t('app.title')}</span>
        <nav className="flex flex-wrap items-center gap-4 text-sm">
          <Link className="text-slate-700 hover:text-brand-mid" to="/dashboard">
            {t('nav.dashboard')}
          </Link>
          {canBrowseDocuments && (
            <>
              <Link className="text-slate-700 hover:text-brand-mid" to="/upload">
                {t('nav.upload')}
              </Link>
              <Link className="text-slate-700 hover:text-brand-mid" to="/search">
                {t('nav.search')}
              </Link>
              <Link className="text-slate-700 hover:text-brand-mid" to="/documents">
                {t('nav.documents')}
              </Link>
            </>
          )}
          {isAdmin && (
            <Link className="text-slate-700 hover:text-brand-mid" to="/admin">
              {t('nav.admin')}
            </Link>
          )}
          {canOcrQueue && (
            <Link className="text-slate-700 hover:text-brand-mid" to="/admin/ocr-queue">
              {t('nav.ocrQueue')}
            </Link>
          )}
          {canAuditLog && (
            <Link className="text-slate-700 hover:text-brand-mid" to="/admin/audit">
              {t('nav.auditLog')}
            </Link>
          )}
          <button type="button" className="text-brand-mid hover:underline" onClick={logout}>
            {t('auth.logout')}
          </button>
        </nav>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
