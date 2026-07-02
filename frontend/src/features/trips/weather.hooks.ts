import { useQuery } from '@tanstack/react-query';
import { weatherApi } from './weather.api';

export function useWeather(
  lat: number | null,
  lng: number | null,
  startDate: string | null,
  endDate: string | null,
) {
  return useQuery({
    queryKey: ['weather', lat, lng, startDate, endDate],
    queryFn: () => weatherApi.get(lat!, lng!, startDate!, endDate!),
    enabled: !!lat && !!lng && !!startDate && !!endDate,
    staleTime: 1000 * 60 * 30,
  });
}
