import { apiClient } from '@/lib/axios';
import type { NearbyResponse, DestinationDetailResponse } from '@/types/api';

export async function fetchNearby(lat: number, lng: number, radius = 20000, limit = 20): Promise<NearbyResponse> {
  const res = await apiClient.get<NearbyResponse>('/api/destinations', {
    params: { lat, lng, radius, limit },
  });
  return res.data;
}

export async function fetchDestinationDetail(providerRef: string): Promise<DestinationDetailResponse> {
  const res = await apiClient.get<DestinationDetailResponse>(`/api/destinations/${encodeURIComponent(providerRef)}`);
  return res.data;
}
