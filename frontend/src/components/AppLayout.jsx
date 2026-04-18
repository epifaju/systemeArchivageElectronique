import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../api/axiosInstance';
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
    const refresh = localStorage.getItem('refreshToken');
    try {
      if (refresh) {
        await api.post('/api/auth/logout', { refreshToken: refresh });
      }
    } catch {
      /* ignore */
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    clear();
    navigate('/login');
  }

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
