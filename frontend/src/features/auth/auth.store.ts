import { create } from 'zustand';
import type { UserInfo } from '@/types/auth';

type AuthState = {
  accessToken: string | null;
  user: UserInfo | null;
  isInitializing: boolean;
  addToTripContext: { destinationRef: string } | null;
  setSession: (token: string, user: UserInfo) => void;
  clearSession: () => void;
  setInitializing: (v: boolean) => void;
  setAddToTripContext: (ctx: { destinationRef: string } | null) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isInitializing: true,
  addToTripContext: null,
  setSession: (token, user) => set({ accessToken: token, user }),
  clearSession: () => set({ accessToken: null, user: null, addToTripContext: null }),
  setInitializing: (v) => set({ isInitializing: v }),
  setAddToTripContext: (ctx) => set({ addToTripContext: ctx }),
}));
