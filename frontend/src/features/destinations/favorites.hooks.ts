import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { favoritesApi } from './favorites.api';

export function useFavorites() {
  return useQuery({
    queryKey: ['favorites'],
    queryFn: favoritesApi.list,
  });
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
}
