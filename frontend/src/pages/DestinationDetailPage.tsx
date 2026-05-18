import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MapPin, Globe, Clock, Star, ArrowLeft } from 'lucide-react';
import { fetchDestinationDetail } from '@/features/destinations/destinations.api';
import { useAuth } from '@/features/auth/useAuth';
import { PhotoCarousel } from '@/features/destinations/PhotoCarousel';
import { AddToTripDropdown } from '@/features/trips/AddToTripDropdown';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';

function DetailSkeleton() {
  return (
    <div className="space-y-6 animate-fade-in">
      {/* Photo skeleton with shimmer */}
      <div className="h-64 w-full rounded-lg animate-shimmer" />

      <div className="space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-3 flex-1">
            <Skeleton className="h-8 w-3/4" />
            <div className="flex items-center gap-2">
              <Skeleton className="h-6 w-20 rounded-full" />
              <Skeleton className="h-5 w-12" />
            </div>
          </div>
          <Skeleton className="h-10 w-28 hidden md:block" />
        </div>

        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-2/3" />

        <div className="space-y-3 pt-2">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-4 rounded-full" />
            <Skeleton className="h-4 w-48" />
          </div>
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-4 rounded-full" />
            <div className="space-y-1.5">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-4 w-32" />
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

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
    return <DetailSkeleton />;
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4 animate-fade-in">
        <h1 className="text-2xl font-bold">Destination not found</h1>
        <p className="text-muted-foreground">This destination may no longer be available.</p>
        <Button asChild>
          <Link to="/">Back to Home</Link>
        </Button>
      </div>
    );
  }

  const handleLoginForTrip = () => {
    setAddToTripContext({ destinationRef: providerRef! });
    navigate('/login');
  };

  return (
    <div className="space-y-6 pb-24 md:pb-0 animate-fade-in">
      {/* Back navigation */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Back
      </button>

      <div className="animate-scale-in">
        <PhotoCarousel photos={data.photos} />
      </div>

      <div className="space-y-4 animate-slide-up" style={{ animationDelay: '100ms' }}>
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
              <AddToTripDropdown
                destinationRef={providerRef!}
                destinationName={data.name}
                photoUrl={data.photos[0]}
              />
            ) : (
              <Popover>
                <PopoverTrigger asChild>
                  <Button size="lg" className="transition-transform hover:scale-105">Add to Trip</Button>
                </PopoverTrigger>
                <PopoverContent className="w-64 animate-scale-in">
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
              className="text-sm text-blue-600 hover:underline transition-colors"
            >
              {data.website}
            </a>
          </div>
        )}
      </div>

      {/* Mobile sticky Add to Trip CTA */}
      <div className="fixed bottom-0 left-0 right-0 p-4 bg-background/80 backdrop-blur-md border-t md:hidden animate-slide-up">
        {isAuthenticated ? (
          <AddToTripDropdown
            destinationRef={providerRef!}
            destinationName={data.name}
            photoUrl={data.photos[0]}
          />
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
