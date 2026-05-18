import { create } from 'zustand';
import type { TripDay } from '@/types/trip';

interface DragState {
  activeDayId: string | null;
  activeItemId: string | null;
  localDays: TripDay[] | null;
  setLocalDays: (days: TripDay[]) => void;
  startDrag: (itemId: string, dayId: string) => void;
  endDrag: () => void;
  resetLocal: () => void;
}

export const useDragStore = create<DragState>((set) => ({
  activeDayId: null,
  activeItemId: null,
  localDays: null,
  setLocalDays: (days) => set({ localDays: days }),
  startDrag: (itemId, dayId) =>
    set({ activeItemId: itemId, activeDayId: dayId }),
  endDrag: () => set({ activeItemId: null, activeDayId: null }),
  resetLocal: () =>
    set({ localDays: null, activeItemId: null, activeDayId: null }),
}));
