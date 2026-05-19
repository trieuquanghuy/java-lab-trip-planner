import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DestinationList } from '../DestinationList';
import type { NearbyItem } from '@/types/api';

const items: NearbyItem[] = [
  { providerRef: 'a', name: 'Place A', lat: 0, lng: 0, category: 'Park', rating: 4.0, photoUrl: null as unknown as string },
  { providerRef: 'b', name: 'Place B', lat: 1, lng: 1, category: 'Museum', rating: 3.5, photoUrl: null as unknown as string },
];

describe('DestinationList', () => {
  it('shows skeleton cards while loading', () => {
    const { container } = render(
      <MemoryRouter>
        <DestinationList items={[]} isLoading={true} />
      </MemoryRouter>,
    );
    // Skeletons use a grid; count children
    const grid = container.querySelector('.grid');
    expect(grid).not.toBeNull();
    expect(grid!.children.length).toBe(6);
  });

  it('shows "No attractions found" when items empty and not loading', () => {
    render(
      <MemoryRouter>
        <DestinationList items={[]} isLoading={false} />
      </MemoryRouter>,
    );
    expect(screen.getByText('No attractions found')).toBeInTheDocument();
  });

  it('renders all destination cards', () => {
    render(
      <MemoryRouter>
        <DestinationList items={items} isLoading={false} />
      </MemoryRouter>,
    );
    expect(screen.getByText('Place A')).toBeInTheDocument();
    expect(screen.getByText('Place B')).toBeInTheDocument();
  });

  it('renders correct number of cards', () => {
    render(
      <MemoryRouter>
        <DestinationList items={items} isLoading={false} />
      </MemoryRouter>,
    );
    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(2);
  });
});
