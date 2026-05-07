import { ReactNode } from 'react';
import { AuthProvider, useAuth } from 'react-oidc-context';
import { useLocation } from 'react-router-dom';
import { WebStorageStateStore } from 'oidc-client-ts';

const AUTHORITY = import.meta.env.VITE_OIDC_AUTHORITY
  ?? 'http://localhost:8180/realms/sigdep';
const CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'sigdep-console';
const REDIRECT_URI = window.location.origin + '/app';
const POST_LOGOUT_URI = window.location.origin + '/';

const oidcConfig = {
  authority: AUTHORITY,
  client_id: CLIENT_ID,
  redirect_uri: REDIRECT_URI,
  post_logout_redirect_uri: POST_LOGOUT_URI,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  automaticSilentRenew: true,
  onSigninCallback: () => {
    // Strip ?code=&state= from the URL after the callback completes.
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

export function SigdepAuthProvider({ children }: { children: ReactNode }) {
  return <AuthProvider {...oidcConfig}>{children}</AuthProvider>;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-ink-muted">
        Chargement de la session…
      </div>
    );
  }

  if (auth.error) {
    return (
      <div className="min-h-screen flex items-center justify-center text-rose-600 text-sm">
        Erreur d’authentification : {auth.error.message}
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    auth.signinRedirect({ state: { from: location.pathname } });
    return null;
  }

  return <>{children}</>;
}

export function getAccessToken(): string | undefined {
  // react-oidc-context stores the user under this key by default.
  const key = `oidc.user:${AUTHORITY}:${CLIENT_ID}`;
  const raw = window.localStorage.getItem(key);
  if (!raw) return undefined;
  try {
    return JSON.parse(raw).access_token;
  } catch {
    return undefined;
  }
}
