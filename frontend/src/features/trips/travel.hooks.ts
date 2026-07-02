import { useQuery } from '@tanstack/react-query';
import { travelApi } from './travel.api';
import type { Waypoint } from '@/types/travel';

export function useTravel(waypoints: Waypoint[]) {
  return useQuery({
    queryKey: ['travel', ...waypoints.map((w) => `${w.lat},${w.lng}`)],
    queryFn: () => travelApi.getSegments(waypoints),
    enabled: waypoints.length >= 2,
    staleTime: 1000 * 60 * 60, // 1h client-side cache
  });
}
