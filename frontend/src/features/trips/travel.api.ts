import { apiClient } from '@/lib/axios';
import type { TravelResponse, Waypoint } from '@/types/travel';

export const travelApi = {
  getSegments: (waypoints: Waypoint[]) =>
    apiClient
      .post<TravelResponse>('/api/travel/segments', { waypoints })
      .then((r) => r.data),
};
