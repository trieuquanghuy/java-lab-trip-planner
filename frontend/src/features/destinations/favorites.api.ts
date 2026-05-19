import { apiClient } from '@/lib/axios';

export interface FavoriteItem {
  destinationRef: string;
  createdAt: string;
}

export interface FavoriteListResponse {
  items: FavoriteItem[];
}

export const favoritesApi = {
  list: () =>
    apiClient.get<FavoriteListResponse>('/api/favorites').then((r) => r.data),

  add: (destinationRef: string) =>
    apiClient
      .post<FavoriteItem>('/api/favorites', { destinationRef })
      .then((r) => r.data),

  remove: (destinationRef: string) =>
    apiClient.delete(`/api/favorites/${destinationRef}`),
};
