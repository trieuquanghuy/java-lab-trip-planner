import { render, screen, within } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import type { Trip, TripDay } from '@/types/trip';

// vi.hoisted ensures mock state is available inside vi.mock factories
const itineraryMocks = vi.hoisted(() => ({
  days: [] as TripDay[],
  activeItemId: null as string | null,
  handleDragStart: vi.fn(),
  handleDragOver: vi.fn(),
  handleDragEnd: vi.fn(),
}));

// ---- Module mocks ----
vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children }: any) => <div data-testid="dnd-context">{children}</div>,
  DragOverlay: ({ children }: any) => <div data-testid="drag-overlay">{children}</div>,
  closestCorners: {},
  PointerSensor: class {},
  KeyboardSensor: class {},
  useSensors: () => [],
  useSensor: () => ({}),
}));

vi.mock('@dnd-kit/sortable', () => ({
  sortableKeyboardCoordinates: {},
}));

vi.mock('../useDragDrop', () => ({
  useDragDrop: () => ({
    days: itineraryMocks.days,
    handleDragStart: itineraryMocks.handleDragStart,
    handleDragOver: itineraryMocks.handleDragOver,
    handleDragEnd: itineraryMocks.handleDragEnd,
  }),
}));

vi.mock('../trip.store', () => ({
  useDragStore: (selector: (s: any) => any) =>
    selector({ activeItemId: itineraryMocks.activeItemId }),
}));

vi.mock('../DayColumn', () => ({
  DayColumn: ({ day }: { day: TripDay }) => <div data-testid={`day-${day.id}`} />,
}));

vi.mock('../DayTabs', () => ({
  DayTabs: () => <div data-testid="day-tabs" />,
}));

vi.mock('../TripEmptyState', () => ({
  TripEmptyState: () => <div data-testid="empty-state" />,
}));

vi.mock('../ItineraryItemCard', () => ({
  ItineraryItemCard: () => <div data-testid="item-card" />,
}));

import { ItineraryBoard } from '../ItineraryBoard';

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
    ],
  },
  { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
];

const mockTrip: Trip = {
  id: 'trip-1',
  name: 'Tokyo Adventure',
  startDate: null,
  endDate: null,
  coverImageUrl: null,
  createdAt: '',
  updatedAt: '',
  days: serverDays,
};

describe('ItineraryBoard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    itineraryMocks.days = [];
    itineraryMocks.activeItemId = null;
  });

  it('renders TripEmptyState when no items exist across all days', () => {
    itineraryMocks.days = [
      { id: 'day-1', dayDate: '2026-01-01', dayIndex: 0, items: [] },
      { id: 'day-2', dayDate: '2026-01-02', dayIndex: 1, items: [] },
    ];
    render(<ItineraryBoard trip={mockTrip} />);
    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
  });

  it('does not render TripEmptyState when at least one day has items', () => {
    itineraryMocks.days = serverDays;
    render(<ItineraryBoard trip={mockTrip} />);
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('renders a DayColumn for each day in the trip (desktop)', () => {
    itineraryMocks.days = serverDays;
    render(<ItineraryBoard trip={mockTrip} />);

    // Desktop section renders all N days; each day's testid must appear
    expect(screen.getAllByTestId('day-day-1').length).toBeGreaterThan(0);
    expect(screen.getByTestId('day-day-2')).toBeInTheDocument();
  });

  it('renders ItineraryItemCard inside DragOverlay for the active dragged item', () => {
    itineraryMocks.days = serverDays;   // serverDays contains item-1 in day-1
    itineraryMocks.activeItemId = 'item-1';
    render(<ItineraryBoard trip={mockTrip} />);

    const overlay = screen.getByTestId('drag-overlay');
    expect(within(overlay).getByTestId('item-card')).toBeInTheDocument();
  });
});
