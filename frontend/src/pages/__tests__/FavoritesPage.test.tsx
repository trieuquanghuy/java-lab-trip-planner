import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';

vi.mock('@/features/destinations/favorites.hooks', () => ({
  useFavoritesEnriched: vi.fn(),
  useRemoveFavorite: vi.fn(),
}));

vi.mock('@/features/destinations/DestinationCard', () => ({
  DestinationCard: ({ destination }: any) => (
    <div data-testid={`card-${destination.providerRef}`}>{destination.name}</div>
  ),
}));

vi.mock('@/features/destinations/DestinationCardSkeleton', () => ({
  DestinationCardSkeleton: () => <div data-testid="skeleton" />,
}));

import { FavoritesPage } from '../FavoritesPage';
import { useFavoritesEnriched, useRemoveFavorite } from '@/features/destinations/favorites.hooks';
import type { NearbyItem } from '@/types/api';

const mockUseFavoritesEnriched = vi.mocked(useFavoritesEnriched);
const mockUseRemoveFavorite = vi.mocked(useRemoveFavorite);

const makeItem = (id: string, name: string): NearbyItem => ({
  providerRef: id,
  name,
  lat: 48.8,
  lng: 2.3,
  category: 'Landmark',
  rating: 4.5,
  photoUrl: null,
});

const defaultRemoveFavorite = { mutate: vi.fn(), isPending: false } as any;

function renderPage() {
  return render(
    <MemoryRouter>
      <FavoritesPage />
    </MemoryRouter>,
  );
}

describe('FavoritesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseRemoveFavorite.mockReturnValue(defaultRemoveFavorite);
  });

  it('shows 6 skeleton cards while loading', () => {
    mockUseFavoritesEnriched.mockReturnValue({
      data: [],
      isLoading: true,
      isError: false,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getAllByTestId('skeleton')).toHaveLength(6);
  });

  it('shows "Failed to load favorites" and a Retry button on error', () => {
    mockUseFavoritesEnriched.mockReturnValue({
      data: [],
      isLoading: false,
      isError: true,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getByText('Failed to load favorites')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('shows "No favorites yet" empty state when isEmpty is true', () => {
    mockUseFavoritesEnriched.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      isEmpty: true,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getByText('No favorites yet')).toBeInTheDocument();
  });

  it('shows "Discover Destinations" link pointing to "/"', () => {
    mockUseFavoritesEnriched.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      isEmpty: true,
      refetch: vi.fn(),
    } as any);

    renderPage();

    const link = screen.getByRole('link', { name: /discover destinations/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/');
  });

  it('renders a destination card for each item in data', () => {
    const items = [makeItem('otm:1', 'Eiffel Tower'), makeItem('otm:2', 'Louvre')];
    mockUseFavoritesEnriched.mockReturnValue({
      data: items,
      isLoading: false,
      isError: false,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getByTestId('card-otm:1')).toBeInTheDocument();
    expect(screen.getByTestId('card-otm:2')).toBeInTheDocument();
  });

  it('shows "2 saved destinations" (plural) when 2 destinations present', () => {
    const items = [makeItem('otm:1', 'Eiffel Tower'), makeItem('otm:2', 'Louvre')];
    mockUseFavoritesEnriched.mockReturnValue({
      data: items,
      isLoading: false,
      isError: false,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getByText('2 saved destinations')).toBeInTheDocument();
  });

  it('shows "1 saved destination" (singular) when 1 destination present', () => {
    const items = [makeItem('otm:1', 'Eiffel Tower')];
    mockUseFavoritesEnriched.mockReturnValue({
      data: items,
      isLoading: false,
      isError: false,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    expect(screen.getByText('1 saved destination')).toBeInTheDocument();
  });

  it('clicking "Remove from favorites" calls removeFavorite.mutate with the correct ref', () => {
    vi.useFakeTimers();
    const mockMutate = vi.fn();
    mockUseRemoveFavorite.mockReturnValue({ mutate: mockMutate, isPending: false } as any);

    const items = [makeItem('otm:1', 'Eiffel Tower')];
    mockUseFavoritesEnriched.mockReturnValue({
      data: items,
      isLoading: false,
      isError: false,
      isEmpty: false,
      refetch: vi.fn(),
    } as any);

    renderPage();

    const button = screen.getByRole('button', { name: /remove from favorites/i });
    fireEvent.click(button);

    vi.advanceTimersByTime(300);

    expect(mockMutate).toHaveBeenCalledWith('otm:1', expect.any(Object));

    vi.useRealTimers();
  });
});
