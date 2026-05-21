import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { searchCities } from './search.api';
import { useDebounce } from '@/hooks/useDebounce';
import type { SearchResponse } from '@/types/api';

export function useSearch(query: string) {
  const debouncedQuery = useDebounce(query, 250);

  return useQuery<SearchResponse>({
    queryKey: ['search', debouncedQuery],
    queryFn: () => searchCities(debouncedQuery),
    enabled: debouncedQuery.length >= 1,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });
}
