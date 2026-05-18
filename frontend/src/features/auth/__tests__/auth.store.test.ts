import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '../auth.store';

describe('auth.store', () => {
  beforeEach(() => {
    useAuthStore.setState({
      accessToken: null,
      user: null,
      isInitializing: true,
      addToTripContext: null,
    });
  });

  it('setSession stores token and user', () => {
    useAuthStore.getState().setSession('tok123', { id: '1', email: 'a@b.com', emailVerified: true });
    const s = useAuthStore.getState();
    expect(s.accessToken).toBe('tok123');
    expect(s.user?.email).toBe('a@b.com');
  });

  it('clearSession resets token, user, and addToTripContext', () => {
    useAuthStore.getState().setSession('tok', { id: '1', email: 'x@y.com', emailVerified: false });
    useAuthStore.getState().setAddToTripContext({ destinationRef: 'otm:123' });
    useAuthStore.getState().clearSession();
    const s = useAuthStore.getState();
    expect(s.accessToken).toBeNull();
    expect(s.user).toBeNull();
    expect(s.addToTripContext).toBeNull();
  });

  it('setAddToTripContext stores deferred intent', () => {
    useAuthStore.getState().setAddToTripContext({ destinationRef: 'fsq:abc' });
    expect(useAuthStore.getState().addToTripContext?.destinationRef).toBe('fsq:abc');
  });

  it('isInitializing starts true and can be set false', () => {
    expect(useAuthStore.getState().isInitializing).toBe(true);
    useAuthStore.getState().setInitializing(false);
    expect(useAuthStore.getState().isInitializing).toBe(false);
  });
});
