import { MapPin } from 'lucide-react';
import { DestinationCard } from './DestinationCard';
import { DestinationCardSkeleton } from './DestinationCardSkeleton';
import type { NearbyItem } from '@/types/api';

interface DestinationListProps {
  items: NearbyItem[];
  isLoading: boolean;
}

export function DestinationList({ items, isLoading }: DestinationListProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <DestinationCardSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-muted-foreground animate-fade-in">
        <MapPin className="h-10 w-10 mb-2" />
        <p>No attractions found</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 stagger-children">
      {items.map((item) => (
        <DestinationCard key={item.providerRef} destination={item} />
      ))}
    </div>
  );
}
