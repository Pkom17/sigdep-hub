import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';

type NavItem = { to: string; label: string };
type NavGroup = { label?: string; items: NavItem[] };

const NAV: NavGroup[] = [
  { items: [
    { to: '/dashboard', label: 'Vue d’ensemble' },
    { to: '/dashboard/pepfar', label: 'Indicateurs PEPFAR' },
  ]},
  { label: 'Données', items: [
    { to: '/dashboard/clinique', label: 'Suivi clinique' },
    { to: '/dashboard/pharmacie', label: 'Pharmacie / ARV' },
    { to: '/dashboard/depistage', label: 'Dépistage' },
    { to: '/dashboard/ptme', label: 'PTME' },
    { to: '/dashboard/tpt', label: 'TPT' },
    { to: '/dashboard/biologie', label: 'Biologie' },
  ]},
  { label: 'Explorer', items: [
    { to: '/dashboard/patients', label: 'Patients' },
    { to: '/dashboard/sites', label: 'Sites' },
  ]},
  { label: 'Admin', items: [
    { to: '/dashboard/sync', label: 'Synchronisation' },
    { to: '/dashboard/users', label: 'Utilisateurs' },
  ]},
];

function initials(name: string | undefined): string {
  if (!name) return '·';
  const parts = name.trim().split(/\s+/);
  return (parts[0]?.[0] ?? '') + (parts[parts.length - 1]?.[0] ?? '');
}

export function AppLayout() {
  const auth = useAuth();
  const profile = auth.user?.profile;
  const displayName = profile?.name
    ?? [profile?.given_name, profile?.family_name].filter(Boolean).join(' ')
    ?? profile?.preferred_username
    ?? '—';

  return (
    <div className="min-h-screen flex bg-slate-50">
      {/* Sidebar */}
      <aside className="w-60 shrink-0 border-r border-slate-200 bg-white flex flex-col">
        <div className="px-4 py-4 border-b border-slate-200 flex items-center gap-2">
          <img src="/logos/sigdep_logo_text_small.png" alt="SIGDEP-3"
               className="h-7 w-auto" />
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3 text-sm">
          {NAV.map((group, gi) => (
            <div key={gi} className={gi > 0 ? 'mt-4' : ''}>
              {group.label && (
                <p className="px-3 mb-1 text-[11px] font-semibold uppercase tracking-wider text-ink-subtle">
                  {group.label}
                </p>
              )}
              {group.items.map(item => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/dashboard'}
                  className={({ isActive }) =>
                    `block px-3 py-2 rounded-md mb-0.5 transition ${
                      isActive
                        ? 'bg-sigdep-50 text-sigdep-700 font-medium'
                        : 'text-ink hover:bg-slate-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      {/* Main column */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-14 border-b border-slate-200 bg-white flex items-center justify-end px-6 gap-3">
          <span className="text-sm text-ink-muted">{displayName}</span>
          <div className="h-8 w-8 rounded-full bg-sigdep-100 text-sigdep-700 flex items-center justify-center text-xs font-semibold uppercase">
            {initials(displayName)}
          </div>
          <button
            onClick={() => auth.signoutRedirect()}
            className="ml-2 text-xs text-ink-muted hover:text-ink underline-offset-2 hover:underline">
            Déconnexion
          </button>
        </header>
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
