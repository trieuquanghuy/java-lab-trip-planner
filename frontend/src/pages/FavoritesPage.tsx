import { useState } from 'react';
import { Heart, Loader2, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useFavoritesEnriched, useRemoveFavorite } from '@/features/destinations/favorites.hooks';
import { DestinationCard } from '@/features/destinations/DestinationCard';
import { DestinationCardSkeleton } from '@/features/destinations/DestinationCardSkeleton';
import { Button } from '@/components/ui/button';

export function FavoritesPage() {
  const { data: destinations, isLoading, isError, refetch, isEmpty } = useFavoritesEnriched();
  const removeFavorite = useRemoveFavorite();
  const [fadingRefs, setFadingRefs] = useState<Set<string>>(new Set());

  const handleUnfavorite = (ref: string) => {
    setFadingRefs((prev) => new Set(prev).add(ref));
    setTimeout(() => {
      removeFavorite.mutate(ref, {
        onError: () => {
          setFadingRefs((prev) => {
            const next = new Set(prev);
            next.delete(ref);
            return next;
          });
          toast.error('Could not remove favorite. Please try again.');
        },
        onSuccess: () => {
          setFadingRefs((prev) => {
            const next = new Set(prev);
            next.delete(ref);
            return next;
          });
        },
      });
    }, 200);
  };

  if (isLoading) {
    return (
      <div className="py-8 animate-fade-in">
        <div className="mb-8">
          <h1 className="text-3xl font-bold">My Favorites</h1>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <DestinationCardSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-muted-foreground mb-4">Failed to load favorites</p>
        <Button onClick={() => refetch()} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <div className="py-8 animate-fade-in">
      <div className="mb-8">
        <h1 className="text-3xl font-bold">My Favorites</h1>
        {destinations.length > 0 && (
          <p className="text-sm text-muted-foreground mt-1">
            {destinations.length} saved destination{destinations.length !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {isEmpty ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
            <Heart className="w-10 h-10 text-primary" />
          </div>
          <h2 className="text-xl font-semibold mb-2">No favorites yet</h2>
          <p className="text-muted-foreground mb-6 max-w-sm">
            Search for destinations and tap the heart icon to save them here.
          </p>
          <Button asChild>
            <Link to="/">
              <Plus className="w-4 h-4 mr-2" />
              Discover Destinations
            </Link>
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {destinations.map((destination) => (
            <div
              key={destination.providerRef}
              className={`relative group transition-opacity duration-200 ${
                fadingRefs.has(destination.providerRef) ? 'opacity-0' : 'opacity-100'
              }`}
            >
              <DestinationCard destination={destination} />
              <button
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  handleUnfavorite(destination.providerRef);
                }}
                className="absolute top-2 right-2 z-10 h-9 w-9 flex items-center justify-center rounded-full bg-background/80 backdrop-blur-sm text-destructive hover:bg-destructive/10 focus-visible:ring-2 focus-visible:ring-ring transition-colors"
                aria-label="Remove from favorites"
              >
                <Heart className="w-4 h-4 fill-current" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
