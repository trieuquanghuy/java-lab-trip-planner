import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import type { TripDay } from '@/types/trip';

// vi.hoisted ensures these are available inside vi.mock factories (which run before imports)
const mocks = vi.hoisted(() => ({
  setLocalDays: vi.fn(),
  startDrag: vi.fn(),
  endDrag: vi.fn(),
  resetLocal: vi.fn(),
  mutate: vi.fn(),
  storeState: { localDays: null as TripDay[] | null },
}));

vi.mock('@dnd-kit/sortable', () => ({
  arrayMove: (arr: unknown[], from: number, to: number) => {
    const a = [...arr];
    const [item] = a.splice(from, 1);
    a.splice(to, 0, item);
    return a;
  },
}));

vi.mock('../trip.store', () => ({
  useDragStore: () => ({
    localDays: mocks.storeState.localDays,
    setLocalDays: mocks.setLocalDays,
    startDrag: mocks.startDrag,
    endDrag: mocks.endDrag,
    resetLocal: mocks.resetLocal,
  }),
}));

vi.mock('../trip.hooks', () => ({
  useUpdateItem: () => ({ mutate: mocks.mutate, isPending: false }),
}));

import { useDragDrop } from '../useDragDrop';

// ---- Shared test fixtures ----
const serverDays: TripDay[] = [
  {
    id: 'day-1',
    dayDate: '2026-01-01',
    dayIndex: 0,
    items: [
      {
        id: 'item-1',
        itineraryDayId: 'day-1',
        destinationRef: 'place-a',
        position: 0,
        timeSlot: null,
        note: null,
        photoUrl: null,
        createdAt: '',
        updatedAt: '',
      },
      {
        id: 'item-2',
        itineraryDayId: 'day-1',
        destinationRef: 'place-b',
        position: 1,
        timeSlot: null,
        note: null,
        photoUrl: null,
        createdAt: '',
        updatedAt: '',
      },
    ],
  },
  { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
];

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children);
}

describe('useDragDrop', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.storeState.localDays = null;
  });

  it('handleDragStart sets activeItemId/activeDayId and clones serverDays into localDays', () => {
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragStart({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
      } as any);
    });

    expect(mocks.startDrag).toHaveBeenCalledWith('item-1', 'day-1');
    expect(mocks.setLocalDays).toHaveBeenCalledWith(serverDays);
  });

  it('handleDragStart does NOT overwrite localDays when they already exist', () => {
    mocks.storeState.localDays = structuredClone(serverDays);
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragStart({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
      } as any);
    });

    expect(mocks.startDrag).toHaveBeenCalledWith('item-1', 'day-1');
    expect(mocks.setLocalDays).not.toHaveBeenCalled();
  });

  it('handleDragOver is a no-op when activeDayId === overDayId', () => {
    mocks.storeState.localDays = structuredClone(serverDays);
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragOver({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: { id: 'item-2', data: { current: { dayId: 'day-1' } } },
      } as any);
    });

    expect(mocks.setLocalDays).not.toHaveBeenCalled();
  });

  it('handleDragOver moves item from source day to target day when crossing days', () => {
    const localDays: TripDay[] = [
      {
        id: 'day-1',
        dayDate: '2026-01-01',
        dayIndex: 0,
        items: [
          {
            id: 'item-1',
            itineraryDayId: 'day-1',
            destinationRef: 'place-a',
            position: 0,
            timeSlot: null,
            note: null,
            photoUrl: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
      },
      { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
    ];
    mocks.storeState.localDays = localDays;
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragOver({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: { id: 'day-2', data: { current: { dayId: 'day-2' } } },
      } as any);
    });

    expect(mocks.setLocalDays).toHaveBeenCalledOnce();
    const [updatedDays] = mocks.setLocalDays.mock.calls[0] as [TripDay[]];
    const day1 = updatedDays.find((d) => d.id === 'day-1')!;
    const day2 = updatedDays.find((d) => d.id === 'day-2')!;
    expect(day1.items).toHaveLength(0);
    expect(day2.items).toHaveLength(1);
    expect(day2.items[0].id).toBe('item-1');
    expect(day2.items[0].itineraryDayId).toBe('day-2');
  });

  it('handleDragEnd calls resetLocal when there is no over target', () => {
    mocks.storeState.localDays = structuredClone(serverDays);
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragEnd({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: null,
      } as any);
    });

    expect(mocks.endDrag).toHaveBeenCalledOnce();
    expect(mocks.resetLocal).toHaveBeenCalledOnce();
  });

  it('handleDragEnd calls resetLocal when dropped on itself (activeId === overId)', () => {
    mocks.storeState.localDays = structuredClone(serverDays);
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragEnd({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: { id: 'item-1' },
      } as any);
    });

    expect(mocks.resetLocal).toHaveBeenCalledOnce();
    expect(mocks.mutate).not.toHaveBeenCalled();
  });

  it('handleDragEnd reorders items within the same day and updates positions', () => {
    mocks.storeState.localDays = [
      {
        id: 'day-1',
        dayDate: '2026-01-01',
        dayIndex: 0,
        items: [
          {
            id: 'item-1',
            itineraryDayId: 'day-1',
            destinationRef: 'place-a',
            position: 0,
            timeSlot: null,
            note: null,
            photoUrl: null,
            createdAt: '',
            updatedAt: '',
          },
          {
            id: 'item-2',
            itineraryDayId: 'day-1',
            destinationRef: 'place-b',
            position: 1,
            timeSlot: null,
            note: null,
            photoUrl: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
      },
      { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
    ];
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    // Move item-1 (index 0) down to item-2's position (index 1)
    act(() => {
      result.current.handleDragEnd({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: { id: 'item-2' },
      } as any);
    });

    expect(mocks.setLocalDays).toHaveBeenCalledOnce();
    const [updatedDays] = mocks.setLocalDays.mock.calls[0] as [TripDay[]];
    const day1 = updatedDays.find((d) => d.id === 'day-1')!;
    expect(day1.items[0].id).toBe('item-2');
    expect(day1.items[0].position).toBe(0);
    expect(day1.items[1].id).toBe('item-1');
    expect(day1.items[1].position).toBe(1);
  });

  it('handleDragEnd calls updateItem.mutate with correct itemId, position, and itineraryDayId', () => {
    mocks.storeState.localDays = [
      {
        id: 'day-1',
        dayDate: '2026-01-01',
        dayIndex: 0,
        items: [
          {
            id: 'item-1',
            itineraryDayId: 'day-1',
            destinationRef: 'place-a',
            position: 0,
            timeSlot: null,
            note: null,
            photoUrl: null,
            createdAt: '',
            updatedAt: '',
          },
          {
            id: 'item-2',
            itineraryDayId: 'day-1',
            destinationRef: 'place-b',
            position: 1,
            timeSlot: null,
            note: null,
            photoUrl: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
      },
      { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
    ];
    const { result } = renderHook(() => useDragDrop('trip-1', serverDays), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.handleDragEnd({
        active: { id: 'item-1', data: { current: { dayId: 'day-1' } } },
        over: { id: 'item-2' },
      } as any);
    });

    // After arrayMove([item-1, item-2], 0, 1) → [item-2, item-1]
    // item-1 lands at index 1, so position = 1
    expect(mocks.mutate).toHaveBeenCalledOnce();
    expect(mocks.mutate).toHaveBeenCalledWith(
      { itemId: 'item-1', data: { position: 1, itineraryDayId: 'day-1' } },
      expect.objectContaining({ onSettled: expect.any(Function) }),
    );
  });
});
