import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CreateUserRequest, UpdateUserRequest, UserDetail, UserRow,
  createUser, fetchUser, fetchUserRoles, fetchUsers, resetUserPassword,
  setUserEnabled, updateUser,
} from '../api/client';
import { formatInt } from '../components/Kpi';
import { PageHeader } from '../components/PageHeader';

function formatTimestamp(ms: number | null): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleDateString('fr-FR',
    { day: '2-digit', month: 'short', year: 'numeric' });
}

type ModalKind =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; userId: string }
  | { kind: 'password'; userId: string; username: string }
  | { kind: 'disable'; user: UserRow };

export function Utilisateurs() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<ModalKind>({ kind: 'none' });
  const size = 50;
  const qc = useQueryClient();

  const users = useQuery({
    queryKey: ['users', query, page],
    queryFn: () => fetchUsers({ q: query, page, size }),
  });

  const totalPages = users.data
    ? Math.max(1, Math.ceil(users.data.total / users.data.size)) : 1;

  function refresh() { qc.invalidateQueries({ queryKey: ['users'] }); }

  return (
    <div className="px-6 py-6">
      <PageHeader
        title="Utilisateurs"
        subtitle={users.data ? `${formatInt(users.data.total)} comptes Keycloak` : 'Chargement…'}
        right={<>
          <input
            type="search"
            value={query}
            onChange={e => { setQuery(e.target.value); setPage(0); }}
            placeholder="Rechercher (nom, email)…"
            className="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm
                       focus:outline-none focus:border-sigdep-500 focus:ring-1 focus:ring-sigdep-500"
          />
          <button
            onClick={() => setModal({ kind: 'create' })}
            className="rounded-md bg-sigdep-600 hover:bg-sigdep-700 text-white px-3 py-2 text-sm">
            Nouvel utilisateur
          </button>
        </>} />

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="thead-sigdep text-left">
            <tr className="text-left">
              <th className="px-4 py-2 font-medium">Identifiant</th>
              <th className="px-4 py-2 font-medium">Nom complet</th>
              <th className="px-4 py-2 font-medium">Email</th>
              <th className="px-4 py-2 font-medium">Statut</th>
              <th className="px-4 py-2 font-medium">Créé le</th>
              <th className="px-4 py-2 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {users.isLoading ? (
              <tr><td colSpan={6} className="px-4 py-6 text-center text-ink-muted">Chargement…</td></tr>
            ) : users.isError ? (
              <tr><td colSpan={6} className="px-4 py-6 text-center text-rose-600">
                Erreur de chargement — vérifie que le client <code>sigdep-console-admin</code> est bien configuré.
              </td></tr>
            ) : users.data?.content.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-6 text-center text-ink-muted">Aucun utilisateur</td></tr>
            ) : users.data?.content.map(u => (
              <tr key={u.id} className="hover:bg-slate-50">
                <td className="px-4 py-2 font-mono text-xs">{u.username}</td>
                <td className="px-4 py-2">
                  {[u.firstName, u.lastName].filter(Boolean).join(' ') || '—'}
                </td>
                <td className="px-4 py-2 text-ink-muted">{u.email ?? '—'}</td>
                <td className="px-4 py-2">
                  {u.enabled
                    ? <span className="text-xs px-2 py-0.5 rounded bg-emerald-50 text-emerald-700">Actif</span>
                    : <span className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-500">Désactivé</span>}
                </td>
                <td className="px-4 py-2 text-ink-muted">{formatTimestamp(u.createdAt)}</td>
                <td className="px-4 py-2 text-right whitespace-nowrap">
                  <button
                    onClick={() => setModal({ kind: 'edit', userId: u.id })}
                    className="text-sigdep-700 hover:underline text-xs mr-3">
                    Éditer
                  </button>
                  <button
                    onClick={() => setModal({ kind: 'password', userId: u.id, username: u.username })}
                    className="text-sigdep-700 hover:underline text-xs mr-3">
                    Mot de passe
                  </button>
                  {u.enabled ? (
                    <button
                      onClick={() => setModal({ kind: 'disable', user: u })}
                      className="text-rose-600 hover:underline text-xs">
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={async () => {
                        await setUserEnabled(u.id, true);
                        refresh();
                      }}
                      className="text-emerald-700 hover:underline text-xs">
                      Réactiver
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {users.data && users.data.total > size && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <p className="text-ink-muted">Page {users.data.page + 1} / {totalPages}</p>
          <div className="flex gap-2">
            <button onClick={() => setPage(p => Math.max(0, p - 1))}
                    disabled={users.data.page === 0}
                    className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
              Précédent
            </button>
            <button onClick={() => setPage(p => p + 1)}
                    disabled={users.data.page + 1 >= totalPages}
                    className="px-3 py-1 rounded border border-slate-300 disabled:opacity-50 hover:bg-slate-50">
              Suivant
            </button>
          </div>
        </div>
      )}

      {modal.kind === 'create' && (
        <CreateModal onClose={() => setModal({ kind: 'none' })} onDone={() => { refresh(); setModal({ kind: 'none' }); }} />
      )}
      {modal.kind === 'edit' && (
        <EditModal userId={modal.userId}
                   onClose={() => setModal({ kind: 'none' })}
                   onDone={() => { refresh(); setModal({ kind: 'none' }); }} />
      )}
      {modal.kind === 'password' && (
        <PasswordModal userId={modal.userId} username={modal.username}
                       onClose={() => setModal({ kind: 'none' })} />
      )}
      {modal.kind === 'disable' && (
        <DisableModal user={modal.user}
                      onClose={() => setModal({ kind: 'none' })}
                      onDone={() => { refresh(); setModal({ kind: 'none' }); }} />
      )}
    </div>
  );
}

// ---------- modals ---------------------------------------------------------

function ModalShell({ title, children, onClose, footer }:
    Readonly<{ title: string; children: React.ReactNode; onClose: () => void; footer?: React.ReactNode }>) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
         onClick={onClose}>
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg" onClick={e => e.stopPropagation()}>
        <div className="px-5 py-3 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-base font-semibold text-sigdep-800">{title}</h3>
          <button onClick={onClose} className="text-ink-muted hover:text-ink text-lg leading-none">&times;</button>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">{children}</div>
        {footer && <div className="px-5 py-3 border-t border-slate-200 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}

function Field({ label, children }: Readonly<{ label: string; children: React.ReactNode }>) {
  return (
    <label className="block">
      <span className="block text-xs font-medium text-ink-muted mb-1">{label}</span>
      {children}
    </label>
  );
}

const inputClass =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-sigdep-500 focus:ring-1 focus:ring-sigdep-500';

function RolesCheckboxes({ roles, value, onChange }:
    Readonly<{ roles: string[]; value: string[]; onChange: (next: string[]) => void }>) {
  const set = new Set(value);
  return (
    <div className="grid grid-cols-2 gap-1 max-h-44 overflow-auto border border-slate-200 rounded-md p-2">
      {roles.length === 0 && <span className="text-xs text-ink-muted col-span-2">Aucun rôle disponible</span>}
      {roles.map(r => {
        const checked = set.has(r);
        return (
          <label key={r} className="flex items-center gap-2 text-xs">
            <input type="checkbox" checked={checked}
                   onChange={() => {
                     const next = new Set(set);
                     if (checked) next.delete(r); else next.add(r);
                     onChange(Array.from(next).sort());
                   }} />
            <span className="font-mono">{r}</span>
          </label>
        );
      })}
    </div>
  );
}

function CreateModal({ onClose, onDone }: Readonly<{ onClose: () => void; onDone: () => void }>) {
  const [form, setForm] = useState<CreateUserRequest>({
    username: '', email: '', firstName: '', lastName: '',
    enabled: true, emailVerified: false,
    password: '', passwordTemporary: true, realmRoles: [],
  });
  const roles = useQuery({ queryKey: ['user-roles'], queryFn: fetchUserRoles });
  const m = useMutation({ mutationFn: () => createUser(form), onSuccess: onDone });

  return (
    <ModalShell title="Nouvel utilisateur" onClose={onClose}
      footer={<>
        <button onClick={onClose} className="px-3 py-1.5 text-sm border border-slate-300 rounded">Annuler</button>
        <button
          onClick={() => m.mutate()}
          disabled={!form.username || m.isPending}
          className="px-3 py-1.5 text-sm rounded bg-sigdep-600 text-white hover:bg-sigdep-700 disabled:opacity-50">
          {m.isPending ? 'Création…' : 'Créer'}
        </button>
      </>}>
      <Field label="Identifiant (username)">
        <input className={inputClass} value={form.username}
               onChange={e => setForm({ ...form, username: e.target.value })} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Prénom">
          <input className={inputClass} value={form.firstName ?? ''}
                 onChange={e => setForm({ ...form, firstName: e.target.value })} />
        </Field>
        <Field label="Nom">
          <input className={inputClass} value={form.lastName ?? ''}
                 onChange={e => setForm({ ...form, lastName: e.target.value })} />
        </Field>
      </div>
      <Field label="Email">
        <input className={inputClass} type="email" value={form.email ?? ''}
               onChange={e => setForm({ ...form, email: e.target.value })} />
      </Field>
      <Field label="Mot de passe initial">
        <input className={inputClass} type="text" value={form.password ?? ''}
               onChange={e => setForm({ ...form, password: e.target.value })} />
      </Field>
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={form.passwordTemporary ?? true}
               onChange={e => setForm({ ...form, passwordTemporary: e.target.checked })} />
        Mot de passe temporaire (l'utilisateur devra le changer)
      </label>
      <Field label="Rôles realm">
        <RolesCheckboxes roles={roles.data ?? []} value={form.realmRoles ?? []}
                         onChange={next => setForm({ ...form, realmRoles: next })} />
      </Field>
      {m.isError && <p className="text-rose-600 text-xs">{(m.error as Error).message}</p>}
    </ModalShell>
  );
}

function EditModal({ userId, onClose, onDone }:
    Readonly<{ userId: string; onClose: () => void; onDone: () => void }>) {
  const detail = useQuery({ queryKey: ['user', userId], queryFn: () => fetchUser(userId) });
  const roles = useQuery({ queryKey: ['user-roles'], queryFn: fetchUserRoles });
  const [form, setForm] = useState<UpdateUserRequest | null>(null);
  const m = useMutation({
    mutationFn: () => updateUser(userId, form ?? {}),
    onSuccess: onDone,
  });

  // Initialise form once detail loads.
  if (detail.data && form === null) {
    const d = detail.data as UserDetail;
    setForm({
      email: d.email ?? '',
      firstName: d.firstName ?? '',
      lastName: d.lastName ?? '',
      enabled: d.enabled,
      emailVerified: d.emailVerified,
      realmRoles: d.realmRoles ?? [],
    });
  }

  return (
    <ModalShell title={`Édition — ${detail.data?.username ?? '…'}`} onClose={onClose}
      footer={<>
        <button onClick={onClose} className="px-3 py-1.5 text-sm border border-slate-300 rounded">Annuler</button>
        <button
          onClick={() => m.mutate()}
          disabled={!form || m.isPending}
          className="px-3 py-1.5 text-sm rounded bg-sigdep-600 text-white hover:bg-sigdep-700 disabled:opacity-50">
          {m.isPending ? 'Enregistrement…' : 'Enregistrer'}
        </button>
      </>}>
      {detail.isLoading || !form ? (
        <p className="text-ink-muted">Chargement…</p>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Prénom">
              <input className={inputClass} value={form.firstName ?? ''}
                     onChange={e => setForm({ ...form, firstName: e.target.value })} />
            </Field>
            <Field label="Nom">
              <input className={inputClass} value={form.lastName ?? ''}
                     onChange={e => setForm({ ...form, lastName: e.target.value })} />
            </Field>
          </div>
          <Field label="Email">
            <input className={inputClass} type="email" value={form.email ?? ''}
                   onChange={e => setForm({ ...form, email: e.target.value })} />
          </Field>
          <label className="flex items-center gap-2 text-xs">
            <input type="checkbox" checked={form.emailVerified ?? false}
                   onChange={e => setForm({ ...form, emailVerified: e.target.checked })} />
            Email vérifié
          </label>
          <label className="flex items-center gap-2 text-xs">
            <input type="checkbox" checked={form.enabled ?? true}
                   onChange={e => setForm({ ...form, enabled: e.target.checked })} />
            Compte actif
          </label>
          <Field label="Rôles realm">
            <RolesCheckboxes roles={roles.data ?? []} value={form.realmRoles ?? []}
                             onChange={next => setForm({ ...form, realmRoles: next })} />
          </Field>
        </>
      )}
      {m.isError && <p className="text-rose-600 text-xs">{(m.error as Error).message}</p>}
    </ModalShell>
  );
}

function PasswordModal({ userId, username, onClose }:
    Readonly<{ userId: string; username: string; onClose: () => void }>) {
  const [password, setPassword] = useState('');
  const [temporary, setTemporary] = useState(true);
  const m = useMutation({
    mutationFn: () => resetUserPassword(userId, password, temporary),
    onSuccess: onClose,
  });
  return (
    <ModalShell title={`Mot de passe — ${username}`} onClose={onClose}
      footer={<>
        <button onClick={onClose} className="px-3 py-1.5 text-sm border border-slate-300 rounded">Annuler</button>
        <button
          onClick={() => m.mutate()}
          disabled={!password || m.isPending}
          className="px-3 py-1.5 text-sm rounded bg-sigdep-600 text-white hover:bg-sigdep-700 disabled:opacity-50">
          {m.isPending ? 'Application…' : 'Réinitialiser'}
        </button>
      </>}>
      <Field label="Nouveau mot de passe">
        <input className={inputClass} type="text" value={password}
               onChange={e => setPassword(e.target.value)} />
      </Field>
      <label className="flex items-center gap-2 text-xs">
        <input type="checkbox" checked={temporary}
               onChange={e => setTemporary(e.target.checked)} />
        Temporaire (l'utilisateur devra le changer à la prochaine connexion)
      </label>
      {m.isError && <p className="text-rose-600 text-xs">{(m.error as Error).message}</p>}
    </ModalShell>
  );
}

function DisableModal({ user, onClose, onDone }:
    Readonly<{ user: UserRow; onClose: () => void; onDone: () => void }>) {
  const m = useMutation({
    mutationFn: () => setUserEnabled(user.id, false),
    onSuccess: onDone,
  });
  return (
    <ModalShell title="Désactiver l'utilisateur" onClose={onClose}
      footer={<>
        <button onClick={onClose} className="px-3 py-1.5 text-sm border border-slate-300 rounded">Annuler</button>
        <button
          onClick={() => m.mutate()}
          disabled={m.isPending}
          className="px-3 py-1.5 text-sm rounded bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50">
          {m.isPending ? 'Désactivation…' : 'Désactiver'}
        </button>
      </>}>
      <p>
        Confirmer la désactivation du compte <span className="font-mono">{user.username}</span> ?
      </p>
      <p className="text-xs text-ink-muted">
        Le compte ne pourra plus se connecter mais ses données restent en place.
        Tu peux réactiver le compte à tout moment depuis la liste.
      </p>
      {m.isError && <p className="text-rose-600 text-xs">{(m.error as Error).message}</p>}
    </ModalShell>
  );
}
