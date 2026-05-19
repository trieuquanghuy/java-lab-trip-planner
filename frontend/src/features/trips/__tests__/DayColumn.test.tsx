import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DayColumn } from '../DayColumn';
import type { TripDay } from '@/types/trip';

// Mock dnd-kit
vi.mock('@dnd-kit/core', () => ({
  useDroppable: () => ({ setNodeRef: vi.fn(), isOver: false }),
}));

vi.mock('@dnd-kit/sortable', () => ({
  SortableContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  verticalListSortingStrategy: {},
  useSortable: () => ({
    attributes: {},
    listeners: {},
    setNodeRef: vi.fn(),
    transform: null,
    transition: undefined,
    isDragging: false,
  }),
}));

vi.mock('@dnd-kit/utilities', () => ({
  CSS: { Transform: { toString: () => undefined } },
}));

vi.mock('../trip.hooks', () => ({
  useUpdateItem: () => ({ mutate: vi.fn(), isPending: false }),
}));

const emptyDay: TripDay = {
  id: 'day-1',
  dayDate: '2026-03-15',
  dayIndex: 0,
  items: [],
};

const dayWithItems: TripDay = {
  id: 'day-1',
  dayDate: '2026-03-15',
  dayIndex: 0,
  items: [
    { id: 'item-1', itineraryDayId: 'day-1', destinationRef: 'tokyo-tower', position: 0, timeSlot: '09:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
    { id: 'item-2', itineraryDayId: 'day-1', destinationRef: 'shibuya-crossing', position: 1, timeSlot: null, note: 'Great spot', photoUrl: null, createdAt: '', updatedAt: '' },
    { id: 'item-3', itineraryDayId: 'day-1', destinationRef: 'meiji-shrine', position: 2, timeSlot: '14:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
  ],
};

describe('DayColumn', () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  function renderColumn(day: TripDay) {
    return render(
      <QueryClientProvider client={qc}>
        <DayColumn day={day} tripId="trip-1" />
      </QueryClientProvider>,
    );
  }

  it('renders day header with date', () => {
    renderColumn(emptyDay);
    expect(screen.getByText(/Day 1/)).toBeInTheDocument();
  });

  it('shows empty placeholder when no items', () => {
    renderColumn(emptyDay);
    expect(screen.getByText('Drop items here')).toBeInTheDocument();
  });

  it('renders items', () => {
    renderColumn(dayWithItems);
    expect(screen.getByText('tokyo-tower')).toBeInTheDocument();
    expect(screen.getByText('shibuya-crossing')).toBeInTheDocument();
    expect(screen.getByText('meiji-shrine')).toBeInTheDocument();
  });

  it('sorts items with time slots first (chronological)', () => {
    const { container } = renderColumn(dayWithItems);
    // Select only item card aria-labels (not the drag handle ones)
    const items = container.querySelectorAll('[aria-label^="Itinerary item:"]');
    const texts = Array.from(items).map(el => el.getAttribute('aria-label'));
    expect(texts[0]).toContain('tokyo-tower'); // 09:00
    expect(texts[1]).toContain('meiji-shrine'); // 14:00
    expect(texts[2]).toContain('shibuya-crossing'); // no time
  });

  it('shows item count badge', () => {
    renderColumn(dayWithItems);
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
