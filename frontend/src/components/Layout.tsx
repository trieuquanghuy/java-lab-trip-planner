import { Outlet, Link } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/auth.store';
import { useAuth } from '@/features/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Toaster } from '@/components/ui/sonner';
import { SkipNavLink } from '@/components/SkipNavLink';
import { MobileNav } from '@/components/MobileNav';
import { ThemeToggle } from '@/components/ThemeToggle';

export function Layout() {
  const isAuthenticated = useAuthStore((s) => !!s.accessToken);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const user = useAuthStore((s) => s.user);
  const { logout } = useAuth();

  return (
    <div className="min-h-screen flex flex-col">
      <SkipNavLink />
      <header className="sticky top-0 z-40 relative border-b border-border bg-background/80 backdrop-blur-md px-4 h-14 flex items-center justify-between transition-all">
        <Link to="/" className="text-lg font-semibold tracking-tight hover:text-primary transition-colors">
          TripPlanner
        </Link>
        <nav className="hidden sm:flex items-center gap-2">
          <ThemeToggle />
          {isInitializing ? (
            <div className="flex items-center gap-2">
              <Skeleton className="h-8 w-20" />
              <Skeleton className="h-8 w-16" />
            </div>
          ) : isAuthenticated ? (
            <div className="flex items-center gap-2 animate-fade-in">
              <span className="text-sm text-muted-foreground truncate max-w-[150px]">
                {user?.email}
              </span>
              <Button variant="ghost" size="sm" asChild>
                <Link to="/trips">My Trips</Link>
              </Button>
              <Button variant="ghost" size="sm" asChild>
                <Link to="/favorites">Favorites</Link>
              </Button>
              <Button variant="ghost" size="sm" onClick={logout}>
                Logout
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2 animate-fade-in">
              <Button variant="ghost" size="sm" asChild>
                <Link to="/login">Login</Link>
              </Button>
              <Button size="sm" asChild>
                <Link to="/signup">Sign Up</Link>
              </Button>
            </div>
          )}
        </nav>
        <MobileNav />
      </header>

      <main id="main-content" className="flex-1 container mx-auto px-4 py-8 md:py-12">
        <Outlet />
      </main>

      <footer className="border-t border-border px-4 py-4 text-center text-sm text-muted-foreground">
        © 2026 TripPlanner
      </footer>
      <Toaster />
    </div>
  );
}
