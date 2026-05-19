import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { NearbyItem } from '@/types/api';

interface DestinationCardProps {
  readonly destination: NearbyItem;
}

export function DestinationCard({ destination }: DestinationCardProps) {
  return (
    <Link to={`/destinations/${destination.providerRef}`} className="block group">
      <Card className="overflow-hidden transition-all duration-300 hover:shadow-lg hover:-translate-y-1">
        {destination.photoUrl ? (
          <div className="relative h-40 w-full overflow-hidden">
            <img
              src={destination.photoUrl}
              alt={destination.name}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
          </div>
        ) : (
          <div className="h-40 bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center transition-all duration-300 group-hover:from-blue-500 group-hover:to-purple-600">
            <MapPin className="h-8 w-8 text-white transition-transform duration-300 group-hover:scale-110" aria-hidden="true" />
          </div>
        )}
        <CardContent className="p-3 space-y-1">
          <h3 className="font-semibold line-clamp-1 group-hover:text-primary transition-colors duration-200">{destination.name}</h3>
          <div className="flex items-center gap-2">
            {destination.category && (
              <Badge variant="secondary" className="text-xs">
                {destination.category}
              </Badge>
            )}
            {destination.rating != null && (
              <span className="text-sm text-muted-foreground">★ {destination.rating.toFixed(1)}</span>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
