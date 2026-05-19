import { describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import { useDragStore } from '../trip.store';

describe('useDragStore', () => {
  beforeEach(() => {
    act(() => {
      useDragStore.getState().resetLocal();
    });
  });

  it('initializes with null state', () => {
    const state = useDragStore.getState();
    expect(state.activeItemId).toBeNull();
    expect(state.activeDayId).toBeNull();
    expect(state.localDays).toBeNull();
  });

  it('startDrag sets activeItemId and activeDayId', () => {
    act(() => {
      useDragStore.getState().startDrag('item-1', 'day-1');
    });
    const state = useDragStore.getState();
    expect(state.activeItemId).toBe('item-1');
    expect(state.activeDayId).toBe('day-1');
  });

  it('endDrag clears active item and day but keeps localDays', () => {
    act(() => {
      useDragStore.getState().setLocalDays([{ id: 'day-1', dayDate: '2026-01-01', dayIndex: 0, items: [] }]);
      useDragStore.getState().startDrag('item-1', 'day-1');
      useDragStore.getState().endDrag();
    });
    const state = useDragStore.getState();
    expect(state.activeItemId).toBeNull();
    expect(state.activeDayId).toBeNull();
    expect(state.localDays).not.toBeNull();
  });

  it('setLocalDays stores days', () => {
    const days = [
      { id: 'day-1', dayDate: '2026-01-01', dayIndex: 0, items: [] },
      { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
    ];
    act(() => {
      useDragStore.getState().setLocalDays(days);
    });
    expect(useDragStore.getState().localDays).toEqual(days);
  });

  it('resetLocal clears everything', () => {
    act(() => {
      useDragStore.getState().startDrag('item-1', 'day-1');
      useDragStore.getState().setLocalDays([{ id: 'day-1', dayDate: '', dayIndex: 0, items: [] }]);
      useDragStore.getState().resetLocal();
    });
    const state = useDragStore.getState();
    expect(state.activeItemId).toBeNull();
    expect(state.activeDayId).toBeNull();
    expect(state.localDays).toBeNull();
  });
});
