import type { ReactNode } from "react";
import { Navigate } from "../routing";
import { useAuth } from "../state/useAuth";

export function RequireAuth({ admin = false, children }: { admin?: boolean; children: ReactNode }) {
  const { session, isAdmin } = useAuth();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (admin && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
