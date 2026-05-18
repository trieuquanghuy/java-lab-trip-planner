import { useAuthStore } from './auth.store';
import { loginApi, signupApi, logoutApi } from './auth.api';
import { queryClient } from '@/lib/queryClient';
import type { LoginRequest, SignupRequest } from '@/types/auth';

export function useAuth() {
  const { accessToken, user, isInitializing, addToTripContext } = useAuthStore();

  const login = async (data: LoginRequest) => {
    const res = await loginApi(data);
    useAuthStore.getState().setSession(res.accessToken, res.user);
    return res;
  };

  const signup = async (data: SignupRequest) => {
    return signupApi(data);
  };

  const logout = async () => {
    try { await logoutApi(); } catch { /* ignore — clear locally regardless */ }
    useAuthStore.getState().clearSession();
    queryClient.clear();
  };

  return {
    isAuthenticated: !!accessToken,
    isInitializing,
    user,
    addToTripContext,
    login,
    signup,
    logout,
    setAddToTripContext: useAuthStore.getState().setAddToTripContext,
  };
}
