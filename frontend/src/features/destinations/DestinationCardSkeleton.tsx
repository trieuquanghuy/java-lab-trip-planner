import { Skeleton } from '@/components/ui/skeleton';

export function DestinationCardSkeleton() {
  return (
    <div className="rounded-lg border bg-card overflow-hidden">
      <div className="relative h-40 w-full animate-shimmer" />
      <div className="p-3 space-y-2">
        <Skeleton className="h-5 w-3/4" />
        <div className="flex items-center gap-2">
          <Skeleton className="h-5 w-16 rounded-full" />
          <Skeleton className="h-4 w-10" />
        </div>
      </div>
    </div>
  );
}
