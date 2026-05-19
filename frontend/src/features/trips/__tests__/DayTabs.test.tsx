import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DayTabs } from '../DayTabs';
import type { TripDay } from '@/types/trip';

const mockDays: TripDay[] = [
  { id: 'day-1', dayDate: '2026-03-15', dayIndex: 0, items: [] },
  { id: 'day-2', dayDate: '2026-03-16', dayIndex: 1, items: [] },
  { id: 'day-3', dayDate: '2026-03-17', dayIndex: 2, items: [] },
];

describe('DayTabs', () => {
  it('renders a tab for each day', () => {
    render(<DayTabs days={mockDays} activeDayId="day-1" onDayChange={vi.fn()} />);
    expect(screen.getByText(/Day 1/)).toBeInTheDocument();
    expect(screen.getByText(/Day 2/)).toBeInTheDocument();
    expect(screen.getByText(/Day 3/)).toBeInTheDocument();
  });

  it('has role="tab" on buttons', () => {
    render(<DayTabs days={mockDays} activeDayId="day-1" onDayChange={vi.fn()} />);
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(3);
  });

  it('marks active tab with aria-selected', () => {
    render(<DayTabs days={mockDays} activeDayId="day-2" onDayChange={vi.fn()} />);
    const tabs = screen.getAllByRole('tab');
    expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
    expect(tabs[0]).toHaveAttribute('aria-selected', 'false');
  });

  it('calls onDayChange when tab is clicked', () => {
    const onDayChange = vi.fn();
    render(<DayTabs days={mockDays} activeDayId="day-1" onDayChange={onDayChange} />);
    fireEvent.click(screen.getAllByRole('tab')[2]);
    expect(onDayChange).toHaveBeenCalledWith('day-3');
  });

  it('shows date on tabs', () => {
    render(<DayTabs days={mockDays} activeDayId="day-1" onDayChange={vi.fn()} />);
    expect(screen.getByText(/Mar 15/)).toBeInTheDocument();
  });
});
