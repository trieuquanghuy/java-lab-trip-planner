import { create } from 'zustand';

type AppState = {
  theme: 'light' | 'dark';
  setTheme: (t: 'light' | 'dark') => void;
};

export const useAppStore = create<AppState>((set) => ({
  theme: 'light',
  setTheme: (t) => set({ theme: t }),
}));
