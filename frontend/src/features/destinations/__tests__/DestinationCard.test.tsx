import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DestinationCard } from '../DestinationCard';
import type { NearbyItem } from '@/types/api';

function renderCard(props: Partial<NearbyItem> = {}) {
  const destination: NearbyItem = {
    providerRef: 'place-123',
    name: 'Eiffel Tower',
    lat: 48.8584,
    lng: 2.2945,
    category: 'Landmark',
    rating: 4.7,
    photoUrl: 'https://example.com/photo.jpg',
    ...props,
  };
  return render(
    <MemoryRouter>
      <DestinationCard destination={destination} />
    </MemoryRouter>,
  );
}

describe('DestinationCard', () => {
  it('renders destination name', () => {
    renderCard();
    expect(screen.getByText('Eiffel Tower')).toBeInTheDocument();
  });

  it('links to destination detail page', () => {
    renderCard();
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/destinations/place-123');
  });

  it('renders photo when photoUrl provided', () => {
    renderCard({ photoUrl: 'https://example.com/img.jpg' });
    const img = screen.getByRole('img');
    expect(img).toHaveAttribute('src', 'https://example.com/img.jpg');
    expect(img).toHaveAttribute('alt', 'Eiffel Tower');
  });

  it('renders placeholder icon when no photoUrl', () => {
    renderCard({ photoUrl: undefined });
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('renders category badge', () => {
    renderCard({ category: 'Museum' });
    expect(screen.getByText('Museum')).toBeInTheDocument();
  });

  it('renders rating with one decimal place', () => {
    renderCard({ rating: 4.5 });
    expect(screen.getByText('★ 4.5')).toBeInTheDocument();
  });

  it('hides rating when null', () => {
    renderCard({ rating: null as unknown as number });
    expect(screen.queryByText(/★/)).not.toBeInTheDocument();
  });

  it('hides category badge when undefined', () => {
    renderCard({ category: undefined });
    expect(screen.queryByText('Landmark')).not.toBeInTheDocument();
  });
});
