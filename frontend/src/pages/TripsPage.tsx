import { useState, useCallback } from 'react';
import { Plus, MapPin, Loader2, AlertTriangle } from 'lucide-react';
import { useInfiniteTrips } from '@/features/trips/trip.hooks';
import { TripCard } from '@/features/trips/TripCard';
import { TripCardSkeleton } from '@/features/trips/TripCardSkeleton';
import { CreateTripWizard } from '@/features/trips/CreateTripWizard';
import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import { Button } from '@/components/ui/button';

export function TripsPage() {
  const [showWizard, setShowWizard] = useState(false);
  const {
    data,
    isLoading,
    isError,
    refetch,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
  } = useInfiniteTrips(12);

  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const sentinelRef = useIntersectionObserver(loadMore, {
    enabled: hasNextPage && !isFetchingNextPage,
  });

  const trips = data?.pages.flatMap((page) => page.content) ?? [];
  const totalElements = data?.pages[0]?.totalElements ?? 0;

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-bold">My Trips</h1>
          {!isLoading && totalElements > 0 && (
            <p className="text-sm text-muted-foreground mt-1">
              {totalElements.toLocaleString()} trips
            </p>
          )}
        </div>
        <button
          onClick={() => setShowWizard(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Create Trip
        </button>
      </div>

      {/* Initial loading state — skeleton grid */}
      {isLoading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {Array.from({ length: 12 }).map((_, i) => (
            <TripCardSkeleton key={i} />
          ))}
        </div>
      )}

      {/* Error state */}
      {isError && !isLoading && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <AlertTriangle className="w-10 h-10 text-destructive mb-4" />
          <p className="text-muted-foreground mb-4">Failed to load trips</p>
          <Button onClick={() => refetch()} variant="outline" size="sm">Retry</Button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && trips.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center animate-fade-in">
          <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
            <MapPin className="w-10 h-10 text-primary" />
          </div>
          <h2 className="text-xl font-semibold mb-2">
            Plan your first adventure
          </h2>
          <p className="text-muted-foreground mb-6 max-w-sm">
            Create a trip, search for destinations, and build your perfect
            day-by-day itinerary.
          </p>
          <button
            onClick={() => setShowWizard(true)}
            className="inline-flex items-center gap-2 px-6 py-3 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create a Trip
          </button>
        </div>
      )}

      {/* Trip grid with lazy loading */}
      {!isLoading && trips.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {trips.map((trip) => (
              <TripCard key={trip.id} trip={trip} />
            ))}
            {/* Loading more skeletons while fetching next page */}
            {isFetchingNextPage &&
              Array.from({ length: 4 }).map((_, i) => (
                <TripCardSkeleton key={`loading-${i}`} />
              ))}
          </div>

          {/* Infinite scroll sentinel */}
          {hasNextPage && (
            <div ref={sentinelRef} className="flex justify-center py-8">
              {isFetchingNextPage && (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span className="text-sm">Loading more trips...</span>
                </div>
              )}
            </div>
          )}

          {/* End of list indicator */}
          {!hasNextPage && trips.length > 12 && (
            <div className="text-center py-8 text-sm text-muted-foreground">
              You've seen all {totalElements.toLocaleString()} trips
            </div>
          )}
        </>
      )}

      <CreateTripWizard open={showWizard} onClose={() => setShowWizard(false)} />
    </div>
  );
}
