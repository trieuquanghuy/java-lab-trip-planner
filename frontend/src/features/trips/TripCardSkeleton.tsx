import { Skeleton } from '@/components/ui/skeleton';

export function TripCardSkeleton() {
  return (
    <div className="rounded-xl overflow-hidden border bg-card shadow-sm">
      <Skeleton className="h-40 w-full animate-shimmer" />
      <div className="p-4 space-y-2">
        <Skeleton className="h-5 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
    </div>
  );
}
