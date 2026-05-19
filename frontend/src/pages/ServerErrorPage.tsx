import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function ServerErrorPage() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20">
      <AlertTriangle className="h-12 w-12 text-destructive" />
      <h1 className="text-4xl font-bold">Server Error</h1>
      <p className="text-muted-foreground">Something went wrong on our end. Please try again in a moment.</p>
      <Button asChild>
        <Link to="/">Go Home</Link>
      </Button>
    </div>
  );
}
