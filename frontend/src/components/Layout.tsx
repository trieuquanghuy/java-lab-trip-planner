import { Outlet, Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

export function Layout() {

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-border px-4 h-14 flex items-center justify-between">
        <Link to="/" className="text-lg font-semibold tracking-tight">
          TripPlanner
        </Link>
        <nav className="flex items-center gap-2">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/login">Login</Link>
          </Button>
          <Button size="sm" asChild>
            <Link to="/signup">Sign Up</Link>
          </Button>
        </nav>
      </header>

      <main className="flex-1 container mx-auto px-4 py-8 md:py-12">
        <Outlet />
      </main>

      <footer className="border-t border-border px-4 py-4 text-center text-sm text-muted-foreground">
        © 2026 TripPlanner
      </footer>
    </div>
  );
}
