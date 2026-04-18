import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { createUser, listUsers, updateUser } from '../api/adminApi';

const ROLES = ['ADMIN', 'ARCHIVISTE', 'AGENT', 'LECTEUR', 'AUDITEUR'];

export default function AdminUsersPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-users', page],
    queryFn: () => listUsers(page, 20),
  });

  const createMut = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      setShowCreate(false);
    },
  });

  const updateMut = useMutation({
    mutationFn: ({ id, body }) => updateUser(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      setEditing(null);
    },
  });

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <Link className="text-sm text-brand-mid hover:underline" to="/admin">
            ← {t('admin.backHub')}
          </Link>
          <h1 className="text-2xl font-semibold text-brand-dark mt-2">{t('admin.usersTitle')}</h1>
        </div>
        <button
          type="button"
          className="rounded bg-brand-mid px-4 py-2 text-sm text-white hover:bg-brand-dark"
          onClick={() => setShowCreate(true)}
        >
          {t('admin.usersCreate')}
        </button>
      </div>

      {isError && (
        <p className="text-red-600 text-sm mb-4">
          {error?.response?.data?.message || error?.message || t('admin.loadError')}
        </p>
      )}

      {isLoading ? (
        <p className="text-slate-600">{t('search.loading')}</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left">
                <tr>
                  <th className="p-2">{t('admin.col.username')}</th>
                  <th className="p-2">{t('admin.col.email')}</th>
                  <th className="p-2">{t('admin.col.fullName')}</th>
                  <th className="p-2">{t('admin.col.role')}</th>
                  <th className="p-2">{t('admin.col.department')}</th>
                  <th className="p-2">{t('admin.col.active')}</th>
                  <th className="p-2 w-28">{t('admin.col.action')}</th>
                </tr>
              </thead>
              <tbody>
                {content.map((u) => (
                  <tr key={u.id} className="border-t border-slate-100">
                    <td className="p-2 font-medium">{u.username}</td>
                    <td className="p-2 text-slate-600">{u.email || '—'}</td>
                    <td className="p-2 text-slate-600">{u.fullName || '—'}</td>
                    <td className="p-2">{t(`admin.roles.${u.role}`)}</td>
                    <td className="p-2 text-slate-600">{u.departmentId ?? '—'}</td>
                    <td className="p-2">{u.active ? t('admin.yes') : t('admin.no')}</td>
                    <td className="p-2">
                      <button
                        type="button"
                        className="text-brand-mid hover:underline text-xs"
                        onClick={() => setEditing(u)}
                      >
                        {t('admin.edit')}
                      </button>
                    </td>
                  </tr>
                ))}
                {!content.length && (
                  <tr>
                    <td colSpan={7} className="p-6 text-center text-slate-500">
                      {t('admin.usersEmpty')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center gap-2">
              <button
                type="button"
                disabled={page <= 0}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                {t('search.prev')}
              </button>
              <span className="text-sm text-slate-600">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
                onClick={() => setPage((p) => p + 1)}
              >
                {t('search.next')}
              </button>
            </div>
          )}
        </>
      )}

      {showCreate && (
        <UserCreateModal
          onClose={() => {
            setShowCreate(false);
            createMut.reset();
          }}
          onSubmit={(body) => createMut.mutate(body)}
          error={createMut.error}
          pending={createMut.isPending}
          t={t}
        />
      )}

      {editing && (
        <UserEditModal
          user={editing}
          onClose={() => {
            setEditing(null);
            updateMut.reset();
          }}
          onSubmit={(body) => updateMut.mutate({ id: editing.id, body })}
          error={updateMut.error}
          pending={updateMut.isPending}
          t={t}
        />
      )}
    </div>
  );
}

function UserCreateModal({ onClose, onSubmit, error, pending, t }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState('AGENT');
  const [departmentId, setDepartmentId] = useState('');
  const [active, setActive] = useState(true);

  function handleSubmit(e) {
    e.preventDefault();
    onSubmit({
      username: username.trim(),
      password,
      email: email.trim() ? email.trim() : null,
      fullName: fullName.trim() || null,
      role,
      departmentId: departmentId.trim() ? Number(departmentId) : null,
      active,
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-brand-dark mb-4">{t('admin.usersCreate')}</h2>
        <form onSubmit={handleSubmit} className="space-y-3 text-sm">
          <label className="block">
            <span className="text-slate-600">{t('admin.col.username')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              autoComplete="off"
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('auth.password')}</span>
            <input
              type="password"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
            />
            <span className="text-xs text-slate-500">{t('admin.passwordHint')}</span>
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.email')}</span>
            <input
              type="email"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.fullName')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.role')}</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {t(`admin.roles.${r}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.department')}</span>
            <input
              type="number"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              placeholder="—"
            />
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            <span className="text-slate-600">{t('admin.col.active')}</span>
          </label>
          {error && (
            <p className="text-red-600 text-xs">
              {error?.response?.data?.message || error?.message}
            </p>
          )}
          <div className="flex gap-2 pt-2">
            <button
              type="submit"
              disabled={pending}
              className="rounded bg-brand-mid px-4 py-2 text-white text-sm disabled:opacity-50"
            >
              {t('admin.save')}
            </button>
            <button type="button" className="rounded border px-4 py-2 text-sm" onClick={onClose}>
              {t('viewer.cancel')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function UserEditModal({ user, onClose, onSubmit, error, pending, t }) {
  const [email, setEmail] = useState(user.email || '');
  const [fullName, setFullName] = useState(user.fullName || '');
  const [role, setRole] = useState(user.role);
  const [departmentId, setDepartmentId] = useState(user.departmentId != null ? String(user.departmentId) : '');
  const [active, setActive] = useState(user.active);
  const [password, setPassword] = useState('');

  function handleSubmit(e) {
    e.preventDefault();
    const body = {
      email: email.trim() || null,
      fullName: fullName.trim() || null,
      role,
      departmentId: departmentId.trim() ? Number(departmentId) : null,
      active,
      password: password.trim() ? password.trim() : null,
    };
    onSubmit(body);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-brand-dark mb-1">
          {t('admin.usersEdit')} — {user.username}
        </h2>
        <form onSubmit={handleSubmit} className="space-y-3 text-sm mt-4">
          <label className="block">
            <span className="text-slate-600">{t('admin.col.email')}</span>
            <input
              type="email"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.fullName')}</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.role')}</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {t(`admin.roles.${r}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.col.department')}</span>
            <input
              type="number"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              placeholder="—"
            />
          </label>
          <label className="block">
            <span className="text-slate-600">{t('admin.newPasswordOptional')}</span>
            <input
              type="password"
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              autoComplete="new-password"
              placeholder="••••••••"
            />
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            <span className="text-slate-600">{t('admin.col.active')}</span>
          </label>
          {error && (
            <p className="text-red-600 text-xs">
              {error?.response?.data?.message || error?.message}
            </p>
          )}
          <div className="flex gap-2 pt-2">
            <button
              type="submit"
              disabled={pending}
              className="rounded bg-brand-mid px-4 py-2 text-white text-sm disabled:opacity-50"
            >
              {t('admin.save')}
            </button>
            <button type="button" className="rounded border px-4 py-2 text-sm" onClick={onClose}>
              {t('viewer.cancel')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
