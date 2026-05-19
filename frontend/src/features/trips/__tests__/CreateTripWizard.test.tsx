import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CreateTripWizard } from '../CreateTripWizard';

const mockMutate = vi.fn();

vi.mock('../trip.hooks', () => ({
  useCreateTrip: () => ({ mutate: mockMutate, isPending: false }),
}));

function renderWizard(open = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onClose = vi.fn();
  return {
    onClose,
    ...render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <CreateTripWizard open={open} onClose={onClose} />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

describe('CreateTripWizard', () => {
  it('renders nothing when closed', () => {
    const { container } = renderWizard(false);
    expect(container.innerHTML).toBe('');
  });

  it('shows step 1 (name input) when opened', () => {
    renderWizard();
    expect(screen.getByText('Name your trip')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('e.g., Tokyo 2026')).toBeInTheDocument();
  });

  it('disables Next when name is empty', () => {
    renderWizard();
    const nextBtn = screen.getByText('Next');
    expect(nextBtn).toBeDisabled();
  });

  it('enables Next after entering name', () => {
    renderWizard();
    const input = screen.getByPlaceholderText('e.g., Tokyo 2026');
    fireEvent.change(input, { target: { value: 'My Trip' } });
    const nextBtn = screen.getByText('Next');
    expect(nextBtn).not.toBeDisabled();
  });

  it('advances to step 2 (dates) on Next', () => {
    renderWizard();
    const input = screen.getByPlaceholderText('e.g., Tokyo 2026');
    fireEvent.change(input, { target: { value: 'My Trip' } });
    fireEvent.click(screen.getByText('Next'));
    expect(screen.getByText('When are you going?')).toBeInTheDocument();
  });

  it('advances to step 3 (confirm) and shows Create Trip button', () => {
    renderWizard();
    const input = screen.getByPlaceholderText('e.g., Tokyo 2026');
    fireEvent.change(input, { target: { value: 'My Trip' } });
    fireEvent.click(screen.getByText('Next')); // to step 2
    fireEvent.click(screen.getByText('Next')); // to step 3
    expect(screen.getByText('Ready to plan!')).toBeInTheDocument();
    expect(screen.getByText('Create Trip')).toBeInTheDocument();
  });

  it('enforces 120 char limit on name', () => {
    renderWizard();
    const input = screen.getByPlaceholderText('e.g., Tokyo 2026') as HTMLInputElement;
    const longName = 'A'.repeat(150);
    fireEvent.change(input, { target: { value: longName } });
    expect(input.value.length).toBeLessThanOrEqual(120);
  });

  it('goes back to step 1 from step 2', () => {
    renderWizard();
    const input = screen.getByPlaceholderText('e.g., Tokyo 2026');
    fireEvent.change(input, { target: { value: 'My Trip' } });
    fireEvent.click(screen.getByText('Next'));
    fireEvent.click(screen.getByText('Back'));
    expect(screen.getByText('Name your trip')).toBeInTheDocument();
  });
});
