import { apiClient } from '@/lib/axios';
import type { WeatherResponse } from '@/types/weather';

export const weatherApi = {
  get: (lat: number, lng: number, startDate: string, endDate: string) =>
    apiClient
      .get<WeatherResponse>('/api/weather', {
        params: { lat, lng, startDate, endDate },
      })
      .then((r) => r.data),
};
