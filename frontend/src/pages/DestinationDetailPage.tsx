import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { MapPin, Globe, Clock, Star } from 'lucide-react';
import { fetchDestinationDetail } from '@/features/destinations/destinations.api';
import { useAuth } from '@/features/auth/useAuth';
import { PhotoCarousel } from '@/features/destinations/PhotoCarousel';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';

export function DestinationDetailPage() {
  const { providerRef } = useParams<{ providerRef: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, setAddToTripContext } = useAuth();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['destination', providerRef],
    queryFn: () => fetchDestinationDetail(providerRef!),
    enabled: !!providerRef,
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-64 w-full rounded-lg" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-20 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <h1 className="text-2xl font-bold">Destination not found</h1>
        <p className="text-muted-foreground">This destination may no longer be available.</p>
        <Button asChild>
          <Link to="/">Back to Home</Link>
        </Button>
      </div>
    );
  }

  const handleAddToTrip = () => {
    if (isAuthenticated) {
      toast.info('Trip management coming soon');
    }
  };

  const handleLoginForTrip = () => {
    setAddToTripContext({ destinationRef: providerRef! });
    navigate('/login');
  };

  return (
    <div className="space-y-6 pb-24 md:pb-0">
      <PhotoCarousel photos={data.photos} />

      <div className="space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-2">
            <h1 className="text-2xl font-bold">{data.name}</h1>
            <div className="flex items-center gap-2">
              {data.category && <Badge variant="secondary">{data.category}</Badge>}
              {data.rating != null && (
                <span className="flex items-center gap-1 text-sm">
                  <Star className="h-4 w-4 fill-yellow-400 text-yellow-400" />
                  {data.rating.toFixed(1)}
                </span>
              )}
            </div>
          </div>

          {/* Desktop Add to Trip CTA */}
          <div className="hidden md:block">
            {isAuthenticated ? (
              <Button size="lg" onClick={handleAddToTrip}>
                Add to Trip
              </Button>
            ) : (
              <Popover>
                <PopoverTrigger asChild>
                  <Button size="lg">Add to Trip</Button>
                </PopoverTrigger>
                <PopoverContent className="w-64">
                  <p className="text-sm mb-3">Log in to add destinations to your trip</p>
                  <Button size="sm" className="w-full" onClick={handleLoginForTrip}>
                    Log in
                  </Button>
                </PopoverContent>
              </Popover>
            )}
          </div>
        </div>

        <p className={`text-muted-foreground ${!data.shortDescription ? 'italic' : ''}`}>
          {data.shortDescription || 'No description available'}
        </p>

        {data.address && (
          <div className="flex items-start gap-2">
            <MapPin className="h-4 w-4 mt-0.5 text-muted-foreground flex-shrink-0" />
            <span className="text-sm">{data.address}</span>
          </div>
        )}

        <div className="flex items-start gap-2">
          <Clock className="h-4 w-4 mt-0.5 text-muted-foreground flex-shrink-0" />
          {data.openingHours ? (
            <div className="text-sm space-y-0.5">
              {Object.entries(data.openingHours).map(([day, hours]) => (
                <div key={day} className="flex gap-2">
                  <span className="font-medium w-20">{day}</span>
                  <span>{hours}</span>
                </div>
              ))}
            </div>
          ) : (
            <span className="text-sm text-muted-foreground italic">Opening hours not available</span>
          )}
        </div>

        {data.website && (
          <div className="flex items-center gap-2">
            <Globe className="h-4 w-4 text-muted-foreground" />
            <a
              href={data.website}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-blue-600 hover:underline"
            >
              {data.website}
            </a>
          </div>
        )}
      </div>

      {/* Mobile sticky Add to Trip CTA */}
      <div className="fixed bottom-0 left-0 right-0 p-4 bg-background border-t md:hidden">
        {isAuthenticated ? (
          <Button size="lg" className="w-full" onClick={handleAddToTrip}>
            Add to Trip
          </Button>
        ) : (
          <Popover>
            <PopoverTrigger asChild>
              <Button size="lg" className="w-full">Add to Trip</Button>
            </PopoverTrigger>
            <PopoverContent className="w-64">
              <p className="text-sm mb-3">Log in to add destinations to your trip</p>
              <Button size="sm" className="w-full" onClick={handleLoginForTrip}>
                Log in
              </Button>
            </PopoverContent>
          </Popover>
        )}
      </div>
    </div>
  );
}
