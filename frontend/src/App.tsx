import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { ErrorBoundary } from 'react-error-boundary';
import { Layout } from '@/components/Layout';
import { HomePage } from '@/pages/HomePage';
import { ErrorBoundaryFallback } from '@/components/ErrorBoundaryFallback';
import { PageLoader } from '@/pages/PageLoader';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { RouteAnnouncer } from '@/components/RouteAnnouncer';

const LoginPage = lazy(() => import('@/pages/LoginPage').then(m => ({ default: m.LoginPage })));
const SignupPage = lazy(() => import('@/pages/SignupPage').then(m => ({ default: m.SignupPage })));
const VerifyEmailPage = lazy(() => import('@/pages/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })));
const DestinationDetailPage = lazy(() => import('@/pages/DestinationDetailPage').then(m => ({ default: m.DestinationDetailPage })));
const TripsPage = lazy(() => import('@/pages/TripsPage').then(m => ({ default: m.TripsPage })));
const TripDetailPage = lazy(() => import('@/pages/TripDetailPage').then(m => ({ default: m.TripDetailPage })));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage').then(m => ({ default: m.NotFoundPage })));
const ServerErrorPage = lazy(() => import('@/pages/ServerErrorPage').then(m => ({ default: m.ServerErrorPage })));
const FavoritesPage = lazy(() => import('@/pages/FavoritesPage').then(m => ({ default: m.FavoritesPage })));
const SharedTripPage = lazy(() => import('@/pages/SharedTripPage').then(m => ({ default: m.SharedTripPage })));

export default function App() {
  return (
    <ErrorBoundary FallbackComponent={ErrorBoundaryFallback}>
      <Suspense fallback={<PageLoader />}>
        <RouteAnnouncer />
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/destinations/:providerRef" element={<DestinationDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/verify" element={<VerifyEmailPage />} />
            <Route path="/error" element={<ServerErrorPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/trips" element={<TripsPage />} />
              <Route path="/trips/:tripId" element={<TripDetailPage />} />
              <Route path="/favorites" element={<FavoritesPage />} />
            </Route>
            <Route path="/share/:token" element={<SharedTripPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
