import { useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { clearSession, getSession, saveSession, type Session } from "../lib/session";
import { AuthContext, type AuthContextValue } from "./authContext";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => getSession());
  const queryClient = useQueryClient();

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
