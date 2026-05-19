import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { TripSummary } from '@/types/trip';

// Mock trip hooks
const mockTrips = {
  data: { pages: [{ content: [] as TripSummary[], totalElements: 0, totalPages: 0, page: 0, size: 12 }] },
  isLoading: false,
  isFetchingNextPage: false,
  hasNextPage: false,
  fetchNextPage: vi.fn(),
};

const mockTripsWithData = {
  data: {
    pages: [{
      content: [
        { id: '1', name: 'Tokyo 2026', startDate: '2026-03-15', endDate: '2026-03-20', coverImageUrl: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
        { id: '2', name: 'Paris Trip', startDate: '2026-06-01', endDate: '2026-06-07', coverImageUrl: 'https://example.com/paris.jpg', createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 12,
    }],
  },
  isLoading: false,
  isFetchingNextPage: false,
  hasNextPage: false,
  fetchNextPage: vi.fn(),
};

const mockLoadingState = {
  data: undefined,
  isLoading: true,
  isFetchingNextPage: false,
  hasNextPage: false,
  fetchNextPage: vi.fn(),
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mockUseInfiniteTrips = vi.fn((): any => mockTrips);

vi.mock('@/features/trips/trip.hooks', () => ({
  useInfiniteTrips: () => mockUseInfiniteTrips(),
  useTrips: vi.fn(),
  useTrip: vi.fn(),
  useCreateTrip: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useUpdateTrip: vi.fn(() => ({ mutate: vi.fn() })),
  useDeleteTrip: vi.fn(() => ({ mutate: vi.fn() })),
  useAddItem: vi.fn(() => ({ mutate: vi.fn() })),
  useUpdateItem: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useDeleteItem: vi.fn(() => ({ mutate: vi.fn() })),
  tripKeys: { all: ['trips'], lists: () => ['trips', 'list'], list: (p: number) => ['trips', 'list', p], details: () => ['trips', 'detail'], detail: (id: string) => ['trips', 'detail', id] },
}));

vi.mock('@/hooks/useIntersectionObserver', () => ({
  useIntersectionObserver: () => ({ current: null }),
}));

import { TripsPage } from '../TripsPage';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/trips']}>
        <TripsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('TripsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseInfiniteTrips.mockReturnValue(mockTrips);
  });

  it('shows empty state when no trips exist', () => {
    renderPage();
    expect(screen.getByText('Plan your first adventure')).toBeInTheDocument();
    expect(screen.getByText('Create a Trip')).toBeInTheDocument();
  });

  it('renders trip cards when trips exist', () => {
    mockUseInfiniteTrips.mockReturnValue(mockTripsWithData);

    renderPage();
    expect(screen.getByText('Tokyo 2026')).toBeInTheDocument();
    expect(screen.getByText('Paris Trip')).toBeInTheDocument();
    expect(screen.getByText('2 trips')).toBeInTheDocument();
  });

  it('shows skeleton loading state', () => {
    mockUseInfiniteTrips.mockReturnValue(mockLoadingState);

    const { container } = renderPage();
    const skeletons = container.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBeGreaterThanOrEqual(1);
  });

  it('has Create Trip button in header', () => {
    mockUseInfiniteTrips.mockReturnValue(mockTripsWithData);

    renderPage();
    const buttons = screen.getAllByText('Create Trip');
    expect(buttons.length).toBeGreaterThanOrEqual(1);
  });

  it('opens wizard when Create Trip is clicked', () => {
    mockUseInfiniteTrips.mockReturnValue(mockTripsWithData);

    renderPage();
    fireEvent.click(screen.getAllByText('Create Trip')[0]);
    expect(screen.getByText('Name your trip')).toBeInTheDocument();
  });
});
