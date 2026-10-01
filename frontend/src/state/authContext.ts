import { createContext } from "react";
import type { Session } from "../lib/session";

export type AuthContextValue = {
  session: Session | null;
  loginWithToken: (token: string) => void;
  logout: () => void;
  isAdmin: boolean;
  /** True while a page load is restoring the session from the refresh cookie. */
  restoring: boolean;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
