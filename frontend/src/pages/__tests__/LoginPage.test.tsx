import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { LoginPage } from '../LoginPage';
import { useAuthStore } from '@/features/auth/auth.store';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/features/auth/auth.api', () => ({
  loginApi: vi.fn(),
  signupApi: vi.fn(),
  refreshApi: vi.fn(),
  logoutApi: vi.fn(),
}));
import { loginApi } from '@/features/auth/auth.api';

function renderWithProviders(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    createElement(
      QueryClientProvider,
      { client: qc },
      createElement(MemoryRouter, null, ui)
    )
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ accessToken: null, user: null, isInitializing: false, addToTripContext: null });
  });

  it('shows validation errors for empty form', async () => {
    renderWithProviders(createElement(LoginPage));
    fireEvent.click(screen.getByRole('button', { name: /log in/i }));
    await waitFor(() => {
      expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
    });
  });

  it('navigates to destination page when addToTripContext exists (TRIP-05)', async () => {
    useAuthStore.setState({ addToTripContext: { destinationRef: 'otm:tokyo_tower' } });
    (loginApi as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      accessToken: 'tok', expiresIn: 900, user: { id: '1', email: 'u@u.com', emailVerified: true },
    });

    renderWithProviders(createElement(LoginPage));
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'u@u.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /log in/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/destinations/otm:tokyo_tower');
    });
  });

  it('navigates to / when no addToTripContext and no next param', async () => {
    (loginApi as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      accessToken: 'tok', expiresIn: 900, user: { id: '1', email: 'u@u.com', emailVerified: true },
    });

    renderWithProviders(createElement(LoginPage));
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'u@u.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /log in/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });
  });
});
