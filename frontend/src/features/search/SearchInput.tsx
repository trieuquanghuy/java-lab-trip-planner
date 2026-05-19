import { useState, useRef } from 'react';
import { Search, MapPin } from 'lucide-react';
import { useSearch } from './useSearch';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import type { CitySearchItem } from '@/types/api';

interface SearchInputProps {
  readonly onCitySelect: (city: CitySearchItem) => void;
}

function SearchResultSkeleton() {
  return (
    <div className="px-3 py-2.5 flex items-center justify-between">
      <div className="flex items-center gap-2">
        <Skeleton className="h-4 w-4 rounded-full" />
        <Skeleton className="h-4 w-32" />
      </div>
      <Skeleton className="h-5 w-12 rounded-full" />
    </div>
  );
}

export function SearchInput({ onCitySelect }: SearchInputProps) {
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const blurTimeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const { data, isFetching, isLoading } = useSearch(query);

  const handleSelect = (city: CitySearchItem) => {
    setQuery(`${city.name}, ${city.country}`);
    setIsOpen(false);
    onCitySelect(city);
  };

  const handleBlur = () => {
    blurTimeoutRef.current = setTimeout(() => setIsOpen(false), 150);
  };

  const handleFocus = () => {
    if (blurTimeoutRef.current) clearTimeout(blurTimeoutRef.current);
    if (query.length >= 1) setIsOpen(true);
  };

  const showDropdown = isOpen && query.length >= 1;
  const hasResults = data?.items && data.items.length > 0;
  const showSkeleton = showDropdown && isLoading && !hasResults;
  const showNoResults = showDropdown && !isLoading && !isFetching && !hasResults && query.length >= 2;

  return (
    <div className="relative w-full">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          type="text"
          placeholder="Search a city..."
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(e.target.value.length >= 1);
          }}
          onFocus={handleFocus}
          onBlur={handleBlur}
          className="w-full pl-9 transition-shadow focus:shadow-md"
        />
        {isFetching && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            <div className="h-4 w-4 border-2 border-muted-foreground/30 border-t-primary rounded-full animate-spin" />
          </div>
        )}
      </div>

      {/* Search results dropdown */}
      {showDropdown && (hasResults || showSkeleton || showNoResults) && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-background border rounded-md shadow-lg z-50 max-h-60 overflow-auto animate-scale-in origin-top">
          {/* Skeleton while first load */}
          {showSkeleton && (
            <div className="divide-y">
              {Array.from({ length: 4 }).map((_, i) => (
                <SearchResultSkeleton key={`skel-${String(i)}`} />
              ))}
            </div>
          )}

          {/* Results */}
          {hasResults && (
            <div className="divide-y">
              {data.items.map((city, idx) => (
                <button
                  key={`${city.name}-${city.country}-${idx}`}
                  type="button"
                  className="w-full px-3 py-2.5 text-left hover:bg-accent text-sm flex items-center gap-2 transition-colors"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => handleSelect(city)}
                >
                  <MapPin className="h-3.5 w-3.5 text-muted-foreground flex-shrink-0" />
                  <span className="flex-1 font-medium truncate">{city.name}, {city.country}</span>
                  <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded-full flex-shrink-0">
                    {city.type}
                  </span>
                </button>
              ))}
            </div>
          )}

          {/* No results */}
          {showNoResults && (
            <div className="px-3 py-4 text-center text-sm text-muted-foreground">
              <p>No cities found for &quot;{query}&quot;</p>
              <p className="text-xs mt-1">Try a different search term</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
