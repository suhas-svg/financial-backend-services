import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { clearSession, getSession, saveSession, SESSION_EXPIRED_EVENT, type Session } from "../lib/session";
import { endSession, refreshSession, SESSION_UPDATED_EVENT } from "../lib/sessionRefresh";
import { AuthContext, type AuthContextValue } from "./authContext";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => getSession());
  const [restoring, setRestoring] = useState(() => getSession() === null);
  const queryClient = useQueryClient();

  // Access tokens live only in memory, so a reload starts without one. Try the
  // httpOnly refresh cookie before treating the user as signed out.
  useEffect(() => {
    if (!restoring) {
      return;
    }
    let cancelled = false;
    void refreshSession().then(() => {
      if (!cancelled) {
        setSession(getSession());
        setRestoring(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [restoring]);

  useEffect(() => {
    const updated = () => setSession(getSession());
    window.addEventListener(SESSION_UPDATED_EVENT, updated);
    return () => window.removeEventListener(SESSION_UPDATED_EVENT, updated);
  }, []);

  useEffect(() => {
    const expire = () => {
      const current = `${window.location.pathname}${window.location.search}`;
      if (!current.startsWith("/login") && !current.startsWith("/register")) {
        window.sessionStorage.setItem("financial-console-return-to", current);
      }
      clearSession();
      queryClient.clear();
      setSession(null);
      const portal = current.startsWith("/admin") ? "&portal=admin" : "";
      window.history.replaceState(null, "", `/login?reason=expired${portal}`);
      window.dispatchEvent(new PopStateEvent("popstate"));
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      loginWithToken: (token) => {
        saveSession(token);
        setSession(getSession());
      },
      logout: () => {
        endSession();
        queryClient.clear();
        setSession(null);
      },
      isAdmin: session?.roles.includes("ROLE_ADMIN") ?? false,
      restoring
    }),
    [queryClient, restoring, session]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
