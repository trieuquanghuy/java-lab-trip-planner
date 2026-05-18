import { useState } from 'react';
import { Plus, MapPin } from 'lucide-react';
import { useTrips } from '@/features/trips/trip.hooks';
import { TripCard } from '@/features/trips/TripCard';
import { TripCardSkeleton } from '@/features/trips/TripCardSkeleton';
import { CreateTripWizard } from '@/features/trips/CreateTripWizard';

export function TripsPage() {
  const [showWizard, setShowWizard] = useState(false);
  const { data, isLoading } = useTrips();

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-3xl font-bold">My Trips</h1>
        <button
          onClick={() => setShowWizard(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Create Trip
        </button>
      </div>

      {isLoading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <TripCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!isLoading && data && data.content.length === 0 && (
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

      {!isLoading && data && data.content.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 stagger-children">
          {data.content.map((trip) => (
            <TripCard key={trip.id} trip={trip} />
          ))}
        </div>
      )}

      <CreateTripWizard open={showWizard} onClose={() => setShowWizard(false)} />
    </div>
  );
}
