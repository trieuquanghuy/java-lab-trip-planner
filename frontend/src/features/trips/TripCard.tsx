import { Link } from 'react-router-dom';
import { Copy } from 'lucide-react';
import { useDuplicateTrip } from './trip.hooks';
import type { TripSummary } from '@/types/trip';

interface Props {
  trip: TripSummary;
}

export function TripCard({ trip }: Props) {
  const dateRange = formatDateRange(trip.startDate, trip.endDate);
  const duplicateTrip = useDuplicateTrip();

  return (
    <div className="relative group">
      <Link
        to={`/trips/${trip.id}`}
        className="block rounded-xl overflow-hidden border bg-card shadow-sm hover:shadow-lg hover:-translate-y-1 transition-all duration-300 animate-fade-in"
      >
        <div className="relative h-40 overflow-hidden bg-gradient-to-br from-primary/20 to-primary/5">
          {trip.coverImageUrl ? (
            <img
              src={trip.coverImageUrl}
              alt={trip.name}
              loading="lazy"
              className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <span className="text-4xl">🗺️</span>
            </div>
          )}
        </div>
        <div className="p-4">
          <h3 className="font-semibold text-lg truncate group-hover:text-primary transition-colors">
            {trip.name}
          </h3>
          {dateRange && (
            <p className="text-sm text-muted-foreground mt-1">{dateRange}</p>
          )}
        </div>
      </Link>
      <button
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          duplicateTrip.mutate(trip.id);
        }}
        disabled={duplicateTrip.isPending}
        className="absolute top-2 right-2 p-2 rounded-lg bg-background/80 backdrop-blur-sm border shadow-sm opacity-0 group-hover:opacity-100 transition-opacity hover:bg-background disabled:opacity-50"
        aria-label="Duplicate trip"
      >
        <Copy className="w-4 h-4" />
      </button>
    </div>
  );
}

function formatDateRange(
  start: string | null,
  end: string | null,
): string | null {
  if (!start) return null;
  const s = new Date(start).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
  if (!end) return s;
  const e = new Date(end).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
  return `${s} – ${e}`;
}
