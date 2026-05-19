import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ItineraryItemCard } from '../ItineraryItemCard';
import type { TripItem } from '@/types/trip';

// Mock dnd-kit sortable
vi.mock('@dnd-kit/sortable', () => ({
  useSortable: () => ({
    attributes: { role: 'button', tabIndex: 0 },
    listeners: {},
    setNodeRef: vi.fn(),
    transform: null,
    transition: null,
    isDragging: false,
  }),
}));

vi.mock('@dnd-kit/utilities', () => ({
  CSS: { Transform: { toString: () => undefined } },
}));

vi.mock('../trip.hooks', () => ({
  useUpdateItem: () => ({ mutate: vi.fn() }),
}));

const mockItem: TripItem = {
  id: 'item-1',
  itineraryDayId: 'day-1',
  destinationRef: 'Eiffel Tower',
  position: 0,
  timeSlot: null,
  note: null,
  photoUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('ItineraryItemCard accessibility', () => {
  it('has focus-visible ring classes for keyboard navigation', () => {
    const { container } = render(
      <ItineraryItemCard item={mockItem} tripId="trip-1" />,
    );
    const card = container.firstElementChild as HTMLElement;
    expect(card.className).toContain('focus-visible:ring-2');
    expect(card.className).toContain('focus-visible:ring-primary');
  });

  it('card is focusable with tabIndex=0', () => {
    const { container } = render(
      <ItineraryItemCard item={mockItem} tripId="trip-1" />,
    );
    const card = container.firstElementChild as HTMLElement;
    expect(card).toHaveAttribute('tabindex', '0');
  });

  it('card has aria-label with item name', () => {
    const { container } = render(
      <ItineraryItemCard item={mockItem} tripId="trip-1" />,
    );
    const card = container.firstElementChild as HTMLElement;
    expect(card).toHaveAttribute('aria-label', 'Itinerary item: Eiffel Tower');
  });

  it('drag handle has aria-label "Drag to reorder"', () => {
    render(<ItineraryItemCard item={mockItem} tripId="trip-1" />);
    expect(screen.getByLabelText('Drag to reorder')).toBeInTheDocument();
  });

  it('time input has aria-label "Set time slot"', () => {
    render(<ItineraryItemCard item={mockItem} tripId="trip-1" />);
    expect(screen.getByLabelText('Set time slot')).toBeInTheDocument();
  });
});
