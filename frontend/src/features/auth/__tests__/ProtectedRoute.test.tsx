import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { act } from '@testing-library/react';
import { useAuthStore } from '../auth.store';
import { ProtectedRoute } from '../ProtectedRoute';

function renderWithRouter(initialEntry = '/protected') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/protected" element={<div>Protected Content</div>} />
        </Route>
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    act(() => {
      useAuthStore.getState().clearSession();
      useAuthStore.getState().setInitializing(false);
    });
  });

  it('shows skeleton while initializing', () => {
    act(() => {
      useAuthStore.getState().setInitializing(true);
    });
    const { container } = renderWithRouter();
    // Skeleton is rendered, not content or login
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
    expect(container.querySelector('[class*="animate-pulse"]')).not.toBeNull();
  });

  it('redirects to login when not authenticated', () => {
    renderWithRouter();
    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('renders outlet when authenticated', () => {
    act(() => {
      useAuthStore.getState().setSession('token', { id: '1', email: 'x@y.com', emailVerified: true });
    });
    renderWithRouter();
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });
});
