import { Skeleton } from '@/components/ui/skeleton';

export function TripCardSkeleton() {
  return (
    <div className="rounded-xl overflow-hidden border bg-card shadow-sm animate-pulse">
      <div className="relative h-40 bg-muted">
        <Skeleton className="absolute inset-0 rounded-none" />
      </div>
      <div className="p-4 space-y-3">
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-4 w-2/5" />
      </div>
    </div>
  );
}
