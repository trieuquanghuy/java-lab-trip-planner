import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TripCard } from '../TripCard';
import type { TripSummary } from '@/types/trip';

const mockTrip: TripSummary = {
  id: '1',
  name: 'Tokyo 2026',
  startDate: '2026-03-15',
  endDate: '2026-03-20',
  coverImageUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockTripWithImage: TripSummary = {
  ...mockTrip,
  id: '2',
  name: 'Paris Trip',
  coverImageUrl: 'https://example.com/paris.jpg',
};

function renderCard(trip: TripSummary = mockTrip) {
  return render(
    <MemoryRouter>
      <TripCard trip={trip} />
    </MemoryRouter>,
  );
}

describe('TripCard', () => {
  it('renders trip name', () => {
    renderCard();
    expect(screen.getByText('Tokyo 2026')).toBeInTheDocument();
  });

  it('renders date range', () => {
    renderCard();
    expect(screen.getByText('Mar 15 – Mar 20')).toBeInTheDocument();
  });

  it('links to trip detail page', () => {
    renderCard();
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/trips/1');
  });

  it('shows cover image when available', () => {
    renderCard(mockTripWithImage);
    const img = screen.getByAltText('Paris Trip');
    expect(img).toHaveAttribute('src', 'https://example.com/paris.jpg');
  });

  it('shows fallback when no cover image', () => {
    renderCard();
    expect(screen.getByText('🗺️')).toBeInTheDocument();
  });

  it('handles null dates gracefully', () => {
    renderCard({ ...mockTrip, startDate: null, endDate: null });
    expect(screen.getByText('Tokyo 2026')).toBeInTheDocument();
    // No date text rendered
    expect(screen.queryByText('Mar')).not.toBeInTheDocument();
  });
});
