import type { ReactNode } from "react";
import { Navigate } from "../routing";
import { useAuth } from "../state/useAuth";

export function RequireAuth({ admin = false, children }: { admin?: boolean; children: ReactNode }) {
  const { session, isAdmin, restoring } = useAuth();
  if (restoring) {
    return (
      <main className="grid min-h-screen place-items-center" role="status" aria-live="polite">
        <span className="text-sm font-medium text-muted">Restoring your session…</span>
      </main>
    );
  }
  if (!session) {
    return <Navigate to={admin ? "/login?portal=admin" : "/login"} replace />;
  }
  if (admin && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
