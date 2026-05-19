import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock auth store to render Layout without auth complications
const mockAuthState = {
  accessToken: null,
  user: null,
  isInitializing: false,
  addToTripContext: null,
  setSession: vi.fn(),
  clearSession: vi.fn(),
  setInitializing: vi.fn(),
  setAddToTripContext: vi.fn(),
};

vi.mock('@/features/auth/auth.store', () => ({
  useAuthStore: Object.assign(
    (selector?: (s: unknown) => unknown) => selector ? selector(mockAuthState) : mockAuthState,
    { getState: () => mockAuthState },
  ),
}));

vi.mock('@/features/auth/useAuth', () => ({
  useAuth: () => ({ logout: vi.fn() }),
}));

import { Layout } from '../../components/Layout';

function renderLayout() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <Layout />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('Dark mode integration', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    });
  });

  it('renders ThemeToggle in the header', () => {
    renderLayout();
    const toggle = screen.getByLabelText(/switch to dark mode/i);
    expect(toggle).toBeInTheDocument();
  });

  it('toggles dark mode class on documentElement when clicked', () => {
    renderLayout();
    const toggle = screen.getByLabelText(/switch to dark mode/i);

    expect(document.documentElement.classList.contains('dark')).toBe(false);

    fireEvent.click(toggle);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByLabelText(/switch to light mode/i)).toBeInTheDocument();
  });

  it('persists theme preference to localStorage', () => {
    renderLayout();
    const toggle = screen.getByLabelText(/switch to dark mode/i);

    fireEvent.click(toggle);
    expect(localStorage.getItem('theme')).toBe('dark');

    fireEvent.click(screen.getByLabelText(/switch to light mode/i));
    expect(localStorage.getItem('theme')).toBe('light');
  });

  it('restores dark mode from localStorage on mount', () => {
    localStorage.setItem('theme', 'dark');
    renderLayout();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByLabelText(/switch to light mode/i)).toBeInTheDocument();
  });
});
