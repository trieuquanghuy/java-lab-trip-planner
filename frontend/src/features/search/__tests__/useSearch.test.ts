import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement, type ReactNode } from 'react';
import { useSearch } from '../useSearch';

vi.mock('../search.api', () => ({
  searchCities: vi.fn().mockResolvedValue({ items: [{ type: 'city', name: 'Tokyo', country: 'JP', lat: 35.6, lng: 139.7 }] }),
}));

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: qc }, children);
  }
  Wrapper.displayName = 'TestQueryWrapper';
  return Wrapper;
}

describe('useSearch', () => {
  it('does not fetch when query is empty', () => {
    const { result } = renderHook(() => useSearch(''), { wrapper: createWrapper() });
    expect(result.current.data).toBeUndefined();
    expect(result.current.isFetching).toBe(false);
  });

  it('fetches after debounce when query has content', async () => {
    const { result } = renderHook(() => useSearch('Tok'), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(result.current.data?.items).toHaveLength(1);
    }, { timeout: 500 });

    expect(result.current.data?.items[0].name).toBe('Tokyo');
  });
});
