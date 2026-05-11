import { useAuth } from "react-oidc-context";
import { NavLink, Outlet } from "react-router-dom";
import { getAccessToken } from "../auth";

type NavItem = { to: string; label: string; requireRoles?: string[] };
type NavGroup = { label?: string; items: NavItem[] };

/**
 * `requireRoles` filters the link by the realm roles in the JWT. Items
 * without it are shown to everyone with a session.
 */
const NAV: NavGroup[] = [
  {
    items: [
      { to: "/app/vue-ensemble", label: "Vue d’ensemble" },
      { to: "/app/pepfar", label: "Indicateurs PEPFAR" },
    ],
  },
  {
    label: "Explorer",
    items: [
      { to: "/app/patients", label: "Patients" },
      { to: "/app/sites", label: "Sites" },
    ],
  },
  {
    label: "Données",
    items: [
      { to: "/app/clinique", label: "Suivi clinique" },
      { to: "/app/pharmacie", label: "Pharmacie / ARV" },
      { to: "/app/depistage", label: "Dépistage" },
      { to: "/app/ptme", label: "PTME" },
      { to: "/app/tpt", label: "TPT" },
      { to: "/app/biologie", label: "Biologie" },
    ],
  },
  {
    label: "Admin",
    items: [
      { to: "/app/sync",  label: "Synchronisation",
        requireRoles: ["SUPER_ADMIN", "IT_ADMIN", "NATIONAL_VIEWER", "AUDITOR"] },
      { to: "/app/users", label: "Utilisateurs",
        requireRoles: ["SUPER_ADMIN", "IT_ADMIN"] },
    ],
  },
];

/**
 * Realm roles live in the *access* token's `realm_access.roles` claim — the
 * ID token (= `user.profile`) doesn't carry them by default in Keycloak. We
 * therefore decode the access token here rather than read the OIDC profile.
 */
function realmRolesFromAccessToken(): Set<string> {
  const token = getAccessToken();
  if (!token) return new Set();
  const parts = token.split(".");
  if (parts.length < 2) return new Set();
  try {
    // JWT payload is base64url (no padding). Convert to base64 then atob.
    const b64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = b64.length % 4 === 0 ? b64 : b64 + "=".repeat(4 - (b64.length % 4));
    const payload = JSON.parse(atob(pad)) as { realm_access?: { roles?: unknown } };
    const roles = payload.realm_access?.roles;
    if (!Array.isArray(roles)) return new Set();
    return new Set(roles.filter((r): r is string => typeof r === "string"));
  } catch {
    return new Set();
  }
}

function initials(name: string | undefined): string {
  if (!name) return "·";
  const parts = name.trim().split(/\s+/);
  return (parts[0]?.[0] ?? "") + (parts[parts.length - 1]?.[0] ?? "");
}

export function AppLayout() {
  const auth = useAuth();
  const profile = auth.user?.profile;
  const displayName =
    profile?.name ??
    [profile?.given_name, profile?.family_name].filter(Boolean).join(" ") ??
    profile?.preferred_username ??
    "—";
  const roles = realmRolesFromAccessToken();

  return (
    <div className="min-h-screen flex bg-slate-50">
      {/* Sidebar */}
      <aside className="w-60 shrink-0 border-r border-slate-200 bg-white flex flex-col">
        <div className="px-4 py-4 border-b border-slate-200 flex items-center gap-2">
          <img src="/logos/sigdep3_crop.png" alt="" className="h-8 w-8" />
          <img
            src="/logos/sigdep_logo_text_small.png"
            alt="SIGDEP-3"
            className="h-7 w-auto"
          />
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3 text-sm">
          {NAV.map((group, gi) => {
            const visible = group.items.filter(
              (it) => !it.requireRoles || it.requireRoles.some((r) => roles.has(r)),
            );
            if (visible.length === 0) return null;
            return (
              <div key={gi} className={gi > 0 ? "mt-4" : ""}>
                {group.label && (
                  <p className="px-3 mb-1 text-[11px] font-semibold uppercase tracking-wider text-ink-subtle">
                    {group.label}
                  </p>
                )}
                {visible.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === "/app/vue-ensemble"}
                    className={({ isActive }) =>
                      `block px-3 py-2 rounded-md mb-0.5 transition ${
                        isActive
                          ? "bg-sigdep-50 text-sigdep-700 font-medium"
                          : "text-ink hover:bg-slate-100"
                      }`
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </div>
            );
          })}
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
            className="ml-2 text-xs text-ink-muted hover:text-ink underline-offset-2 hover:underline"
          >
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
