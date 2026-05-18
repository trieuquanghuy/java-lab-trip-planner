import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { NearbyItem } from '@/types/api';

interface DestinationCardProps {
  destination: NearbyItem;
}

export function DestinationCard({ destination }: DestinationCardProps) {
  return (
    <Link to={`/destinations/${destination.providerRef}`} className="block">
      <Card className="overflow-hidden hover:shadow-md transition-shadow">
        {destination.photoUrl ? (
          <img
            src={destination.photoUrl}
            alt={destination.name}
            loading="lazy"
            className="h-40 w-full object-cover"
          />
        ) : (
          <div className="h-40 bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center">
            <MapPin className="h-8 w-8 text-white" />
          </div>
        )}
        <CardContent className="p-3 space-y-1">
          <h3 className="font-semibold line-clamp-1">{destination.name}</h3>
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
