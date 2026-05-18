import { Routes, Route } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { NotFoundPage } from '@/pages/NotFoundPage';

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
        <Route path="/" element={<PlaceholderPage name="Home" />} />
        <Route path="/destinations/:providerRef" element={<PlaceholderPage name="Destination Detail" />} />
        <Route path="/login" element={<PlaceholderPage name="Login" />} />
        <Route path="/signup" element={<PlaceholderPage name="Sign Up" />} />
        <Route path="/verify" element={<PlaceholderPage name="Verify Email" />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
