import { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import { useSharedTrip } from '@/features/trips/trip.hooks';
import { TripMap, type MarkerData } from '@/features/trips/TripMap';
import { fetchDestinationDetail } from '@/features/destinations/destinations.api';
import { Skeleton } from '@/components/ui/skeleton';

export function SharedTripPage() {
  const { token } = useParams<{ token: string }>();
  const { data: trip, isLoading, error } = useSharedTrip(token ?? '');

  const destinationRefs = useMemo(() => {
    if (!trip) return [];
    const refs = new Set<string>();
    trip.days.forEach((day) => day.items.forEach((item) => refs.add(item.destinationRef)));
    return Array.from(refs);
  }, [trip]);

  const destQueries = useQueries({
    queries: destinationRefs.map((ref) => ({
      queryKey: ['destination', ref],
      queryFn: () => fetchDestinationDetail(ref),
      staleTime: 1000 * 60 * 30,
    })),
  });

  const markers: MarkerData[] = useMemo(() => {
    return destQueries
      .filter((q) => q.data)
      .map((q) => ({
        id: q.data!.providerRef,
        name: q.data!.name,
        lat: q.data!.lat,
        lng: q.data!.lng,
      }));
  }, [destQueries]);

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto py-10 px-4 space-y-6 animate-fade-in">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-40" />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-32 rounded-xl" />
        ))}
      </div>
    );
  }

  if (error || !trip) {
    return (
      <div className="max-w-3xl mx-auto py-20 px-4 text-center">
        <p className="text-xl font-semibold mb-2">Trip not found</p>
        <p className="text-muted-foreground text-sm">
          This link may have been revoked or is invalid.
        </p>
      </div>
    );
  }

  const dateRange = trip.startDate
    ? `${new Date(trip.startDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}${
        trip.endDate
          ? ` – ${new Date(trip.endDate).toLocaleDateString('en-US', {
              month: 'short',
              day: 'numeric',
              year: 'numeric',
            })}`
          : ''
      }`
    : null;

  return (
    <div className="max-w-3xl mx-auto py-8 px-4 space-y-6 animate-fade-in">
      <div>
        <h1 className="text-3xl font-bold">{trip.name}</h1>
        {dateRange && <p className="text-muted-foreground mt-1">{dateRange}</p>}
        <p className="text-xs text-muted-foreground mt-2">Read-only shared view</p>
      </div>

      {markers.length > 0 && (
        <div className="h-64 rounded-xl overflow-hidden border">
          <TripMap markers={markers} />
        </div>
      )}

      <div className="space-y-6">
        {trip.days.length === 0 && (
          <p className="text-muted-foreground text-sm">No days planned yet.</p>
        )}
        {trip.days.map((day) => (
          <div key={day.id} className="rounded-xl border p-4">
            <h2 className="font-semibold mb-3">
              {day.dayDate
                ? new Date(day.dayDate).toLocaleDateString('en-US', {
                    weekday: 'long',
                    month: 'long',
                    day: 'numeric',
                  })
                : `Day ${day.dayIndex + 1}`}
            </h2>
            {day.items.length === 0 ? (
              <p className="text-sm text-muted-foreground">No items</p>
            ) : (
              <ul className="space-y-2">
                {day.items.map((item) => (
                  <li
                    key={item.id}
                    className="flex items-start gap-3 rounded-lg bg-muted/50 px-3 py-2"
                  >
                    {item.photoUrl && (
                      <img
                        src={item.photoUrl}
                        alt=""
                        className="w-10 h-10 rounded object-cover shrink-0"
                      />
                    )}
                    <div className="min-w-0">
                      <p className="text-sm font-medium truncate">{item.destinationRef}</p>
                      {item.timeSlot && (
                        <p className="text-xs text-muted-foreground">{item.timeSlot}</p>
                      )}
                      {item.note && (
                        <p className="text-xs text-muted-foreground">{item.note}</p>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
