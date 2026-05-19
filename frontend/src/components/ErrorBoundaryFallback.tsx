import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ErrorBoundaryFallbackProps {
  error: unknown;
  resetErrorBoundary: () => void;
}

export function ErrorBoundaryFallback({ resetErrorBoundary }: ErrorBoundaryFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20">
      <AlertTriangle className="h-12 w-12 text-destructive" />
      <h1 className="text-2xl font-bold">Something went wrong</h1>
      <p className="text-muted-foreground">We hit an unexpected error. Your data is safe.</p>
      <Button onClick={resetErrorBoundary}>Try again</Button>
    </div>
  );
}
