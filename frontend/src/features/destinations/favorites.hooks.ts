import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { favoritesApi, type FavoriteListResponse } from './favorites.api';
import { fetchBatchDestinations } from './destinations.api';
import type { NearbyItem } from '@/types/api';

export function useFavorites() {
  return useQuery({
    queryKey: ['favorites'],
    queryFn: favoritesApi.list,
  });
}

export function useFavoritesEnriched() {
  const { data: favoritesData, isLoading: favLoading, isError: favError, refetch } = useFavorites();
  const refs = favoritesData?.items.map((f) => f.destinationRef) ?? [];
  const sortedRefs = [...refs].sort();

  const batchQuery = useQuery({
    queryKey: ['destinations', 'batch', ...sortedRefs],
    queryFn: () => fetchBatchDestinations(refs),
    enabled: refs.length > 0,
  });

  const isLoading = favLoading || (refs.length > 0 && batchQuery.isLoading);
  const isError = favError || batchQuery.isError;
  const data: NearbyItem[] = batchQuery.data?.items ?? [];

  return { data, isLoading, isError, refetch, isEmpty: !favLoading && refs.length === 0 };
}

export function useAddFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (destinationRef: string) => favoritesApi.add(destinationRef),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
}

export function useRemoveFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (destinationRef: string) => favoritesApi.remove(destinationRef),
    onMutate: async (destinationRef) => {
      await queryClient.cancelQueries({ queryKey: ['favorites'] });
      const previousFavorites = queryClient.getQueryData<FavoriteListResponse>(['favorites']);
      queryClient.setQueryData<FavoriteListResponse>(['favorites'], (old) => {
        if (!old) return old;
        return { items: old.items.filter((f) => f.destinationRef !== destinationRef) };
      });
      return { previousFavorites };
    },
    onError: (_err, _destinationRef, context) => {
      if (context?.previousFavorites) {
        queryClient.setQueryData(['favorites'], context.previousFavorites);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
}
