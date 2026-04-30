import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../state/AuthProvider";

export function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { session, isAdmin } = useAuth();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (admin && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
