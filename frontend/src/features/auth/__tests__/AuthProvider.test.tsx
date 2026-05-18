import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { AuthProvider } from '../AuthProvider';
import { useAuthStore } from '../auth.store';

vi.mock('../auth.api', () => ({
  refreshApi: vi.fn(),
}));

import { refreshApi } from '../auth.api';

describe('AuthProvider', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null, isInitializing: true, addToTripContext: null });
    vi.clearAllMocks();
  });

  it('sets isInitializing to false after successful refresh', async () => {
    const payload = btoa(JSON.stringify({ sub: 'user-1', email: 'test@test.com', ver: true }));
    const fakeJwt = `header.${payload}.signature`;
    (refreshApi as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ accessToken: fakeJwt, expiresIn: 900 });

    render(<AuthProvider><div>child</div></AuthProvider>);

    await waitFor(() => {
      expect(useAuthStore.getState().isInitializing).toBe(false);
    });
    expect(useAuthStore.getState().accessToken).toBe(fakeJwt);
    expect(useAuthStore.getState().user?.email).toBe('test@test.com');
  });

  it('sets isInitializing to false after failed refresh (no session)', async () => {
    (refreshApi as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('401'));

    render(<AuthProvider><div>child</div></AuthProvider>);

    await waitFor(() => {
      expect(useAuthStore.getState().isInitializing).toBe(false);
    });
    expect(useAuthStore.getState().accessToken).toBeNull();
  });
});
