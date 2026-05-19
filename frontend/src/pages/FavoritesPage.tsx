import { Heart, Loader2, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useFavorites, useRemoveFavorite } from '@/features/destinations/favorites.hooks';
import { Button } from '@/components/ui/button';

export function FavoritesPage() {
  const { data, isLoading, isError, refetch } = useFavorites();
  const removeFavorite = useRemoveFavorite();

  if (isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
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

  const favorites = data?.items ?? [];

  return (
    <div className="py-8 animate-fade-in">
      <div className="mb-8">
        <h1 className="text-3xl font-bold">My Favorites</h1>
        {favorites.length > 0 && (
          <p className="text-sm text-muted-foreground mt-1">
            {favorites.length} saved destination{favorites.length !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {favorites.length === 0 ? (
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
          {favorites.map((fav) => (
            <div
              key={fav.destinationRef}
              className="flex items-center justify-between p-4 rounded-xl border bg-card hover:shadow-md transition-shadow"
            >
              <Link
                to={`/destinations/${fav.destinationRef}`}
                className="flex-1 min-w-0 hover:text-primary transition-colors"
              >
                <p className="font-medium truncate">{fav.destinationRef}</p>
                <p className="text-xs text-muted-foreground">
                  Saved {new Date(fav.createdAt).toLocaleDateString()}
                </p>
              </Link>
              <Button
                variant="ghost"
                size="icon"
                className="h-9 w-9 text-destructive hover:text-destructive"
                onClick={() => removeFavorite.mutate(fav.destinationRef)}
                aria-label="Remove from favorites"
              >
                <Heart className="w-4 h-4 fill-current" />
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
