import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';

vi.mock('@/features/auth/AuthProvider', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const mockAuthState = { accessToken: null, user: null, isInitializing: false, addToTripContext: null, setSession: vi.fn(), clearSession: vi.fn(), setInitializing: vi.fn(), setAddToTripContext: vi.fn() };

vi.mock('@/features/auth/auth.store', () => ({
  useAuthStore: Object.assign(
    (selector?: (s: unknown) => unknown) => selector ? selector(mockAuthState) : mockAuthState,
    { getState: () => mockAuthState }
  ),
}));

function renderApp(route = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('App routing', () => {
  it('renders home page at /', () => {
    renderApp('/');
    expect(screen.getByText(/discover your next/i)).toBeInTheDocument();
  });

  it('renders 404 for unknown routes', async () => {
    renderApp('/unknown-path');
    expect(await screen.findByText(/404/i)).toBeInTheDocument();
  });

  it('renders login page at /login', async () => {
    renderApp('/login');
    expect(await screen.findByRole('button', { name: /log in/i })).toBeInTheDocument();
  });
});
