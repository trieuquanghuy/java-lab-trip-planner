import { useState, useRef } from 'react';
import { useSearch } from './useSearch';
import { Input } from '@/components/ui/input';
import type { CitySearchItem } from '@/types/api';

interface SearchInputProps {
  onCitySelect: (city: CitySearchItem) => void;
}

export function SearchInput({ onCitySelect }: SearchInputProps) {
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const blurTimeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const { data } = useSearch(query);

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

  return (
    <div className="relative w-full">
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
        className="w-full"
      />
      {isOpen && data?.items && data.items.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-background border rounded-md shadow-lg z-50 max-h-60 overflow-auto">
          {data.items.map((city, idx) => (
            <button
              key={`${city.name}-${city.country}-${idx}`}
              type="button"
              className="w-full px-3 py-2 text-left hover:bg-accent text-sm flex items-center justify-between"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => handleSelect(city)}
            >
              <span className="font-medium">{city.name}, {city.country}</span>
              <span className="text-xs text-muted-foreground">{city.type}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
