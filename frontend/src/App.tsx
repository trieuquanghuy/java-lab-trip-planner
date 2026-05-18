import { Routes, Route } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { VerifyEmailPage } from '@/pages/VerifyEmailPage';
import { HomePage } from '@/pages/HomePage';
import { DestinationDetailPage } from '@/pages/DestinationDetailPage';
import { TripsPage } from '@/pages/TripsPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';

function PlaceholderPage({ name }: { name: string }) {
  return (
    <div className="py-8">
      <h1 className="text-xl font-semibold">{name}</h1>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/destinations/:providerRef" element={<DestinationDetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/verify" element={<VerifyEmailPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/trips" element={<TripsPage />} />
          <Route path="/trips/:tripId" element={<PlaceholderPage name="Trip Detail" />} />
          <Route path="/favorites" element={<PlaceholderPage name="Favorites" />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
