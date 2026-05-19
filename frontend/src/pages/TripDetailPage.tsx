import { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Map as MapIcon, X } from 'lucide-react';
import { useQueries } from '@tanstack/react-query';
import { useTrip, useUpdateTrip } from '@/features/trips/trip.hooks';
import { ItineraryBoard } from '@/features/trips/ItineraryBoard';
import { TripMap, type MarkerData } from '@/features/trips/TripMap';
import { fetchDestinationDetail } from '@/features/destinations/destinations.api';
import { Skeleton } from '@/components/ui/skeleton';

export function TripDetailPage() {
  const { tripId } = useParams<{ tripId: string }>();
  const navigate = useNavigate();
  const { data: trip, isLoading, error } = useTrip(tripId ?? '');
  const updateTrip = useUpdateTrip(tripId ?? '');
  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState('');
  const [showMap, setShowMap] = useState(false);

  // Collect unique destinationRefs from all items
  const destinationRefs = useMemo(() => {
    if (!trip) return [];
    const refs = new Set<string>();
    trip.days.forEach((day) => day.items.forEach((item) => refs.add(item.destinationRef)));
    return Array.from(refs);
  }, [trip]);

  // Fetch destination details in parallel to get lat/lng
  const destQueries = useQueries({
    queries: destinationRefs.map((ref) => ({
      queryKey: ['destination', ref],
      queryFn: () => fetchDestinationDetail(ref),
      staleTime: 1000 * 60 * 30, // cache 30 min
      enabled: showMap,
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
      <div className="py-8 space-y-6 animate-fade-in">
        <div className="flex items-center gap-4">
          <Skeleton className="h-8 w-8 rounded" />
          <Skeleton className="h-8 w-64" />
        </div>
        <div className="flex gap-4 overflow-hidden">
          {[1, 2, 3].map((i) => (
            <Skeleton
              key={i}
              className="min-w-[300px] h-[400px] rounded-xl animate-shimmer"
            />
          ))}
        </div>
      </div>
    );
  }

  if (error || !trip) {
    return (
      <div className="py-8 text-center">
        <p className="text-muted-foreground mb-4">Trip not found</p>
        <button
          onClick={() => navigate('/trips')}
          className="text-primary hover:underline"
        >
          Back to trips
        </button>
      </div>
    );
  }

  const handleNameClick = () => {
    setNameValue(trip.name);
    setEditingName(true);
  };

  const handleNameBlur = () => {
    setEditingName(false);
    if (nameValue.trim() && nameValue.trim() !== trip.name) {
      updateTrip.mutate({ name: nameValue.trim() });
    }
  };

  const handleNameKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      (e.target as HTMLInputElement).blur();
    }
    if (e.key === 'Escape') {
      setEditingName(false);
    }
  };

  const dateRange = trip.startDate
    ? `${new Date(trip.startDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}${
        trip.endDate
          ? ` – ${new Date(trip.endDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
          : ''
      }`
    : null;

  return (
    <div className="py-6 animate-fade-in overflow-hidden">
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/trips')}
          className="p-2 h-11 w-11 sm:h-9 sm:w-9 flex items-center justify-center rounded-lg hover:bg-muted transition-colors"
          aria-label="Back to trips"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1 min-w-0">
          {editingName ? (
            <input
              type="text"
              value={nameValue}
              onChange={(e) => setNameValue(e.target.value.slice(0, 120))}
              onBlur={handleNameBlur}
              onKeyDown={handleNameKeyDown}
              className="text-2xl font-bold bg-transparent border-b-2 border-primary focus:outline-none w-full"
              autoFocus
            />
          ) : (
            <h1
              className="text-2xl font-bold cursor-pointer hover:text-primary transition-colors truncate"
              onClick={handleNameClick}
            >
              {trip.name}
            </h1>
          )}
          {dateRange && (
            <p className="text-sm text-muted-foreground">{dateRange}</p>
          )}
        </div>
        <button
          onClick={() => setShowMap(!showMap)}
          className={`p-2 h-11 w-11 sm:h-9 sm:w-9 flex items-center justify-center rounded-lg transition-colors ${
            showMap ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'
          }`}
          aria-label={showMap ? 'Hide map' : 'Show map'}
        >
          {showMap ? <X className="w-5 h-5" /> : <MapIcon className="w-5 h-5" />}
        </button>
      </div>

      <div className="flex flex-col lg:flex-row gap-4 h-[calc(100vh-180px)]">
        <div className={`flex-1 overflow-x-auto ${showMap ? 'hidden lg:block' : ''}`}>
          <ItineraryBoard trip={trip} />
        </div>
        {showMap && (
          <div className="w-full lg:w-[400px] lg:min-w-[400px] h-64 lg:h-full rounded-xl overflow-hidden border">
            <TripMap markers={markers} />
          </div>
        )}
      </div>
    </div>
  );
}
