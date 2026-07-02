import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@/features/auth/useAuth';
import { DestinationDetailPage } from '@/pages/DestinationDetailPage';

// ─── Mocks ──────────────────────────────────────────────────────────────────

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useParams: () => ({ providerRef: 'otm:abc123' }),
    useNavigate: () => vi.fn(),
  };
});

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();
  return { ...actual, useQuery: vi.fn() };
});

vi.mock('@/features/destinations/destinations.api', () => ({
  fetchDestinationDetail: vi.fn(),
}));

vi.mock('@/features/auth/useAuth', () => ({
  useAuth: vi.fn(),
}));

vi.mock('@/features/destinations/PhotoCarousel', () => ({
  PhotoCarousel: ({ photos }: { photos: string[] }) => (
    <div data-testid="photo-carousel" data-count={photos.length} />
  ),
}));

vi.mock('@/features/trips/AddToTripDropdown', () => ({
  AddToTripDropdown: () => <div data-testid="add-to-trip-dropdown" />,
}));

// ─── Helpers ─────────────────────────────────────────────────────────────────

const mockDestination = {
  providerRef: 'otm:abc123',
  name: 'Eiffel Tower',
  category: 'Landmark',
  shortDescription: 'Famous iron tower',
  rating: 4.8,
  lat: 48.8584,
  lng: 2.2945,
  address: 'Champ de Mars, Paris',
  website: 'https://toureiffel.paris',
  photos: ['https://photo1.jpg', 'https://photo2.jpg'],
  openingHours: { Monday: '9:00–23:00', Tuesday: '9:00–23:00' },
  isFavorite: false,
  updatedAt: '2026-01-01T00:00:00Z',
};

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={makeQueryClient()}>
        <DestinationDetailPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

function mockAuthAs(isAuthenticated: boolean) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated,
    setAddToTripContext: vi.fn(),
    isInitializing: false,
    user: null,
    addToTripContext: null,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
  } as ReturnType<typeof useAuth>);
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('DestinationDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthAs(true);
  });

  it('shows skeleton while loading', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    } as ReturnType<typeof useQuery>);

    renderPage();

    // DetailSkeleton renders a div with animate-shimmer (the photo placeholder)
    expect(document.querySelector('.animate-shimmer')).toBeTruthy();
  });

  it('shows "Destination not found" when query errors', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByRole('heading', { name: /destination not found/i })).toBeInTheDocument();
  });

  it('renders the destination name as heading on success', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByRole('heading', { name: 'Eiffel Tower' })).toBeInTheDocument();
  });

  it('renders PhotoCarousel with correct photos prop', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    const carousel = screen.getByTestId('photo-carousel');
    expect(carousel).toBeInTheDocument();
    expect(carousel).toHaveAttribute('data-count', '2');
  });

  it('renders the category badge', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByText('Landmark')).toBeInTheDocument();
  });

  it('shows each opening hours entry when openingHours is present', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByText('Monday')).toBeInTheDocument();
    expect(screen.getByText('Tuesday')).toBeInTheDocument();
    // Both days share the same hours string; getAllByText handles multiple matches
    const hourEntries = screen.getAllByText('9:00–23:00');
    expect(hourEntries).toHaveLength(2);
  });

  it('shows "Opening hours not available" when openingHours is null', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: { ...mockDestination, openingHours: null },
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByText(/opening hours not available/i)).toBeInTheDocument();
  });

  it('shows address when present', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.getByText('Champ de Mars, Paris')).toBeInTheDocument();
  });

  it('shows website link with correct href when present', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    const link = screen.getByRole('link', { name: /toureiffel\.paris/i });
    expect(link).toHaveAttribute('href', 'https://toureiffel.paris');
  });

  it('does NOT show website section when website is null', () => {
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: { ...mockDestination, website: null },
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.queryByRole('link', { name: /toureiffel\.paris/i })).not.toBeInTheDocument();
  });

  it('shows AddToTripDropdown when user is authenticated', () => {
    mockAuthAs(true);
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    // Rendered in both desktop + mobile sticky bar → at least one present
    expect(screen.getAllByTestId('add-to-trip-dropdown').length).toBeGreaterThanOrEqual(1);
  });

  it('does NOT show AddToTripDropdown and shows popover trigger when NOT authenticated', () => {
    mockAuthAs(false);
    vi.mocked(useQuery).mockReturnValue({
      isLoading: false,
      isError: false,
      data: mockDestination,
    } as ReturnType<typeof useQuery>);

    renderPage();

    expect(screen.queryByTestId('add-to-trip-dropdown')).not.toBeInTheDocument();
    // Both desktop + mobile render an "Add to Trip" popover trigger button
    const addButtons = screen.getAllByRole('button', { name: /add to trip/i });
    expect(addButtons.length).toBeGreaterThanOrEqual(1);
  });
});
