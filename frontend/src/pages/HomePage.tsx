import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { SearchInput } from '@/features/search/SearchInput';
import { DestinationList } from '@/features/destinations/DestinationList';
import { fetchNearby } from '@/features/destinations/destinations.api';
import type { CitySearchItem } from '@/types/api';

export function HomePage() {
  const [selectedCity, setSelectedCity] = useState<CitySearchItem | null>(null);

  const { data: nearbyData, isLoading } = useQuery({
    queryKey: ['destinations', selectedCity?.lat, selectedCity?.lng],
    queryFn: () => fetchNearby(selectedCity!.lat, selectedCity!.lng, 20000),
    enabled: !!selectedCity,
  });

  return (
    <div className="space-y-8">
      <div className="flex flex-col items-center text-center py-16 max-w-2xl mx-auto space-y-4">
        <h1 className="text-3xl font-bold">Discover your next destination</h1>
        <p className="text-muted-foreground">
          Search a city to explore nearby attractions
        </p>
        <div className="w-full max-w-md">
          <SearchInput onCitySelect={setSelectedCity} />
        </div>
      </div>

      {selectedCity && (
        <div className="space-y-4">
          <p className="text-lg text-muted-foreground">
            Showing attractions near {selectedCity.name}, {selectedCity.country}
          </p>
          <DestinationList items={nearbyData?.items ?? []} isLoading={isLoading} />
        </div>
      )}
    </div>
  );
}
