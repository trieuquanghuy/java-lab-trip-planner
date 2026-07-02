import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('../favorites.api', () => ({
  favoritesApi: {
    list: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('../destinations.api', () => ({
  fetchBatchDestinations: vi.fn(),
}));

import { favoritesApi, type FavoriteListResponse } from '../favorites.api';
import { fetchBatchDestinations } from '../destinations.api';
import { useFavorites, useFavoritesEnriched, useAddFavorite, useRemoveFavorite } from '../favorites.hooks';

const mockFavoritesApi = vi.mocked(favoritesApi);
const mockFetchBatch = vi.mocked(fetchBatchDestinations);

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useFavorites', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns data from favoritesApi.list', async () => {
    const payload: FavoriteListResponse = {
      items: [{ destinationRef: 'otm:1', createdAt: '2024-01-01T00:00:00Z' }],
    };
    mockFavoritesApi.list.mockResolvedValue(payload);

    const { result } = renderHook(() => useFavorites(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(payload);
  });
});

describe('useFavoritesEnriched', () => {
  beforeEach(() => vi.clearAllMocks());

  it('isEmpty is true when favorites list is empty and not loading', async () => {
    mockFavoritesApi.list.mockResolvedValue({ items: [] });

    const { result } = renderHook(() => useFavoritesEnriched(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isEmpty).toBe(true));
    expect(result.current.isLoading).toBe(false);
  });

  it('isEmpty is false when favorites has items', async () => {
    mockFavoritesApi.list.mockResolvedValue({
      items: [{ destinationRef: 'otm:1', createdAt: '2024-01-01T00:00:00Z' }],
    });
    mockFetchBatch.mockResolvedValue({
      items: [{ providerRef: 'otm:1', name: 'Eiffel Tower', lat: 48.8, lng: 2.3, category: 'Landmark', rating: 4.5, photoUrl: null }],
    });

    const { result } = renderHook(() => useFavoritesEnriched(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isEmpty).toBe(false));
  });

  it('batch query is disabled when refs is empty', async () => {
    mockFavoritesApi.list.mockResolvedValue({ items: [] });

    const { result } = renderHook(() => useFavoritesEnriched(), { wrapper: makeWrapper() });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(mockFetchBatch).not.toHaveBeenCalled();
  });
});

describe('useAddFavorite', () => {
  beforeEach(() => vi.clearAllMocks());

  it('calls favoritesApi.add with destinationRef and invalidates favorites on success', async () => {
    const item = { destinationRef: 'otm:5', createdAt: '2024-01-01T00:00:00Z' };
    mockFavoritesApi.add.mockResolvedValue(item);
    mockFavoritesApi.list.mockResolvedValue({ items: [] });

    const { result } = renderHook(() => useAddFavorite(), { wrapper: makeWrapper() });

    await act(async () => {
      result.current.mutate('otm:5');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockFavoritesApi.add).toHaveBeenCalledWith('otm:5');
  });
});

describe('useRemoveFavorite', () => {
  beforeEach(() => vi.clearAllMocks());

  it('optimistically removes item from cache on mutate', async () => {
    const existingData: FavoriteListResponse = {
      items: [
        { destinationRef: 'otm:1', createdAt: '2024-01-01T00:00:00Z' },
        { destinationRef: 'otm:2', createdAt: '2024-01-02T00:00:00Z' },
      ],
    };

    let resolveRemove!: () => void;
    mockFavoritesApi.remove.mockImplementation(
      () => new Promise((res) => { resolveRemove = () => res({ status: 204 }); }),
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    queryClient.setQueryData(['favorites'], existingData);

    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);

    const { result } = renderHook(() => useRemoveFavorite(), { wrapper });

    act(() => {
      result.current.mutate('otm:1');
    });

    await waitFor(() => {
      const cached = queryClient.getQueryData<FavoriteListResponse>(['favorites']);
      expect(cached?.items.map((f) => f.destinationRef)).not.toContain('otm:1');
    });

    resolveRemove();
  });

  it('rolls back on error (restores previousFavorites)', async () => {
    const existingData: FavoriteListResponse = {
      items: [{ destinationRef: 'otm:1', createdAt: '2024-01-01T00:00:00Z' }],
    };

    mockFavoritesApi.remove.mockRejectedValue(new Error('Network error'));

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    queryClient.setQueryData(['favorites'], existingData);

    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);

    const { result } = renderHook(() => useRemoveFavorite(), { wrapper });

    await act(async () => {
      result.current.mutate('otm:1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    const cached = queryClient.getQueryData<FavoriteListResponse>(['favorites']);
    expect(cached?.items.map((f) => f.destinationRef)).toContain('otm:1');
  });
});
