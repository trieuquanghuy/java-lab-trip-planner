import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TripEmptyState } from '../TripEmptyState';

describe('TripEmptyState', () => {
  it('renders illustration and CTA', () => {
    render(
      <MemoryRouter>
        <TripEmptyState />
      </MemoryRouter>,
    );
    expect(screen.getByText('Plan your adventure')).toBeInTheDocument();
    expect(screen.getByText('Add your first stop')).toBeInTheDocument();
  });

  it('has descriptive text', () => {
    render(
      <MemoryRouter>
        <TripEmptyState />
      </MemoryRouter>,
    );
    expect(screen.getByText(/Search for destinations/)).toBeInTheDocument();
  });
});
