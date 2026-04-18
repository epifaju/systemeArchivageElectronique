import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import { useAuthStore } from '../store/authStore';

export default function LoginPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      const { data } = await api.post('/api/auth/login', { username, password });
      localStorage.setItem('accessToken', data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      setSession(data);
      navigate('/dashboard');
    } catch {
      setError('401');
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-brand-dark mb-6">{t('app.title')}</h1>
        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-slate-600 mb-1">{t('auth.username')}</label>
            <input
              className="w-full rounded border border-slate-300 px-3 py-2"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
            />
          </div>
          <div>
            <label className="block text-sm text-slate-600 mb-1">{t('auth.password')}</label>
            <input
              type="password"
              className="w-full rounded border border-slate-300 px-3 py-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>
          {error && <p className="text-sm text-red-600">{t('auth.login')} — erreur</p>}
          <button
            type="submit"
            className="w-full rounded bg-brand-mid py-2 text-white hover:bg-brand-dark"
          >
            {t('auth.submit')}
          </button>
        </form>
        <div className="mt-6 flex gap-2 justify-end">
          <button type="button" className="text-sm text-brand-mid" onClick={() => i18n.changeLanguage('fr')}>
            {t('language.fr')}
          </button>
          <button type="button" className="text-sm text-brand-mid" onClick={() => i18n.changeLanguage('pt')}>
            {t('language.pt')}
          </button>
        </div>
      </div>
    </div>
  );
}
