import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { clearSession, getSession, saveSession, SESSION_EXPIRED_EVENT, type Session } from "../lib/session";
import { AuthContext, type AuthContextValue } from "./authContext";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => getSession());
  const queryClient = useQueryClient();

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
        clearSession();
        queryClient.clear();
        setSession(null);
      },
      isAdmin: session?.roles.includes("ROLE_ADMIN") ?? false
    }),
    [queryClient, session]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
