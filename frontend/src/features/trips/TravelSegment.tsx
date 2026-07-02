interface TravelSegmentProps {
  durationMinutes: number | null;
  distanceKm: number | null;
}

export function TravelSegment({ durationMinutes, distanceKm }: TravelSegmentProps) {
  return (
    <div className="flex items-center justify-center gap-2 py-1 text-xs text-muted-foreground">
      <div className="h-px flex-1 bg-border" />
      <span>
        🚗{' '}
        {durationMinutes != null && distanceKm != null
          ? `${Math.round(durationMinutes)} min · ${distanceKm.toFixed(1)} km`
          : 'Travel time unavailable'}
      </span>
      <div className="h-px flex-1 bg-border" />
    </div>
  );
}
