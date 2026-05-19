import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { MapPin, Compass, Globe } from 'lucide-react';
import { SearchInput } from '@/features/search/SearchInput';
import { DestinationList } from '@/features/destinations/DestinationList';
import { fetchNearby } from '@/features/destinations/destinations.api';
import { Button } from '@/components/ui/button';
import type { CitySearchItem } from '@/types/api';

const FEATURED_CITIES = [
  { name: 'Paris', country: 'France', lat: 48.8566, lng: 2.3522, emoji: '🇫🇷' },
  { name: 'Tokyo', country: 'Japan', lat: 35.6762, lng: 139.6503, emoji: '🇯🇵' },
  { name: 'New York', country: 'United States', lat: 40.7128, lng: -74.006, emoji: '🇺🇸' },
  { name: 'London', country: 'United Kingdom', lat: 51.5074, lng: -0.1278, emoji: '🇬🇧' },
  { name: 'Rome', country: 'Italy', lat: 41.9028, lng: 12.4964, emoji: '🇮🇹' },
  { name: 'Barcelona', country: 'Spain', lat: 41.3874, lng: 2.1686, emoji: '🇪🇸' },
];

export function HomePage() {
  const [selectedCity, setSelectedCity] = useState<CitySearchItem | null>(null);

  const { data: nearbyData, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: ['destinations', selectedCity?.lat, selectedCity?.lng],
    queryFn: () => fetchNearby(selectedCity!.lat, selectedCity!.lng, 20000),
    enabled: !!selectedCity,
    placeholderData: keepPreviousData,
  });

  const handleFeaturedClick = (city: typeof FEATURED_CITIES[number]) => {
    setSelectedCity({ name: city.name, country: city.country, lat: city.lat, lng: city.lng, type: 'city' });
  };

  return (
    <div className="space-y-8">
      {/* Hero Section */}
      <div className="flex flex-col items-center text-center py-16 max-w-2xl mx-auto space-y-6 animate-fade-in">
        <div className="flex items-center gap-2 text-primary">
          <Compass className="h-6 w-6 animate-spin" style={{ animationDuration: '8s' }} aria-hidden="true" />
          <span className="text-sm font-medium uppercase tracking-wider">Trip Planner</span>
        </div>
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight animate-slide-up">
          Discover your next{' '}
          <span className="bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
            destination
          </span>
        </h1>
        <p className="text-lg text-muted-foreground animate-slide-up" style={{ animationDelay: '100ms' }}>
          Search a city to explore nearby attractions and plan your perfect trip
        </p>
        <div className="w-full max-w-md animate-slide-up" style={{ animationDelay: '200ms' }}>
          <SearchInput onCitySelect={setSelectedCity} />
        </div>
      </div>

      {/* Featured Cities (shown when no city is selected) */}
      {!selectedCity && (
        <div className="space-y-4 animate-fade-in" style={{ animationDelay: '300ms' }}>
          <div className="flex items-center gap-2 text-muted-foreground">
            <Globe className="h-4 w-4" />
            <span className="text-sm font-medium">Popular Destinations</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 stagger-children">
            {FEATURED_CITIES.map((city) => (
              <button
                key={city.name}
                onClick={() => handleFeaturedClick(city)}
                className="group flex flex-col items-center gap-2 p-4 rounded-xl border bg-card transition-all duration-300 hover:shadow-md hover:-translate-y-1 hover:border-primary/30"
              >
                <span className="text-2xl transition-transform duration-300 group-hover:scale-125">{city.emoji}</span>
                <span className="text-sm font-medium group-hover:text-primary transition-colors">{city.name}</span>
                <span className="text-xs text-muted-foreground">{city.country}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Results Section */}
      {selectedCity && (
        <div className="space-y-4 animate-slide-up">
          <div className="flex items-center gap-2">
            <MapPin className="h-4 w-4 text-primary" />
            <p className="text-lg text-muted-foreground">
              Showing attractions near <span className="font-medium text-foreground">{selectedCity.name}, {selectedCity.country}</span>
            </p>
            {isFetching && !isLoading && (
              <div className="h-4 w-4 border-2 border-muted-foreground/30 border-t-primary rounded-full animate-spin" />
            )}
          </div>
          {isError ? (
            <div className="flex flex-col items-center justify-center gap-4 py-12">
              <p className="text-muted-foreground">Failed to load attractions</p>
              <Button onClick={() => refetch()} variant="outline" size="sm">Retry</Button>
            </div>
          ) : (
            <DestinationList items={nearbyData?.items ?? []} isLoading={isLoading} />
          )}
        </div>
      )}
    </div>
  );
}
