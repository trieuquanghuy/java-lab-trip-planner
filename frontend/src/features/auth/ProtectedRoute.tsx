import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from './auth.store';
import { Skeleton } from '@/components/ui/skeleton';

export function ProtectedRoute() {
  const isAuthenticated = useAuthStore((s) => !!s.accessToken);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const location = useLocation();

  if (isInitializing) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Skeleton className="h-8 w-48" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  }

  return <Outlet />;
}
