import { apiClient } from '@/lib/axios';
import type { SearchResponse } from '@/types/api';

export async function searchCities(q: string, limit = 5): Promise<SearchResponse> {
  const res = await apiClient.get<SearchResponse>('/api/search', {
    params: { q, limit },
  });
  return res.data;
}
