import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from 'react-bootstrap';
import { useAuth } from './AuthContext';

/** Protege las rutas: exige sesión y, opcionalmente, un permiso. */
export default function ProtectedRoute({ permission }: { permission?: string }) {
  const { user, loading, mustChangePassword, can } = useAuth();
  const loc = useLocation();

  if (loading) return <div className="d-flex justify-content-center py-5"><Spinner /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: loc.pathname }} />;
  if (mustChangePassword && loc.pathname !== '/change-password') return <Navigate to="/change-password" replace />;
  if (permission && !can(permission)) {
    return <div className="alert alert-warning m-4">No tienes permiso para ver esta sección.</div>;
  }
  return <Outlet />;
}
