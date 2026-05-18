import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from './auth.store';
import { refreshApi } from './auth.api';

export function AuthProvider({ children }: { children: ReactNode }) {
  const setSession = useAuthStore((s) => s.setSession);
  const setInitializing = useAuthStore((s) => s.setInitializing);

  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      try {
        const data = await refreshApi();
        if (cancelled) return;
        const payload = JSON.parse(atob(data.accessToken.split('.')[1]));
        setSession(data.accessToken, {
          id: payload.sub,
          email: payload.email,
          emailVerified: payload.ver ?? false,
        });
      } catch {
        // No valid session — user is logged out
      } finally {
        if (!cancelled) setInitializing(false);
      }
    }

    restoreSession();
    return () => { cancelled = true; };
  }, [setSession, setInitializing]);

  return <>{children}</>;
}
