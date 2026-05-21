import { Link } from 'react-router-dom';
import type { TripSummary } from '@/types/trip';

interface Props {
  trip: TripSummary;
}

export function TripCard({ trip }: Props) {
  const dateRange = formatDateRange(trip.startDate, trip.endDate);

  return (
    <Link
      to={`/trips/${trip.id}`}
      className="group block rounded-xl overflow-hidden border bg-card shadow-sm hover:shadow-lg hover:-translate-y-1 transition-all duration-300 animate-fade-in"
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
