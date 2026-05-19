import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { useAuthStore } from '@/features/auth/auth.store';
import { useAuth } from '@/features/auth/useAuth';
import { Button } from '@/components/ui/button';

export function MobileNav() {
  const [isOpen, setIsOpen] = useState(false);
  const isAuthenticated = useAuthStore((s) => !!s.accessToken);
  const user = useAuthStore((s) => s.user);
  const { logout } = useAuth();

  return (
    <div className="sm:hidden">
      <Button
        variant="ghost"
        size="icon"
        onClick={() => setIsOpen(!isOpen)}
        aria-label={isOpen ? 'Close menu' : 'Open menu'}
        aria-expanded={isOpen}
        aria-controls="mobile-nav"
        className="h-11 w-11"
      >
        {isOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
      </Button>

      {isOpen && (
        <div
          id="mobile-nav"
          className="absolute top-14 left-0 right-0 z-50 border-b border-border bg-background p-4 space-y-2 animate-slide-up"
        >
          {isAuthenticated ? (
            <>
              <p className="text-sm text-muted-foreground truncate px-2">{user?.email}</p>
              <hr className="border-border" />
              <Link
                to="/trips"
                onClick={() => setIsOpen(false)}
                className="block py-3 px-2 text-sm font-medium hover:bg-muted rounded-md"
              >
                My Trips
              </Link>
              <button
                onClick={() => { logout(); setIsOpen(false); }}
                className="block w-full text-left py-3 px-2 text-sm font-medium hover:bg-muted rounded-md"
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                onClick={() => setIsOpen(false)}
                className="block py-3 px-2 text-sm font-medium hover:bg-muted rounded-md"
              >
                Login
              </Link>
              <Link
                to="/signup"
                onClick={() => setIsOpen(false)}
                className="block py-3 px-2 text-sm font-medium hover:bg-muted rounded-md"
              >
                Sign Up
              </Link>
            </>
          )}
        </div>
      )}
    </div>
  );
}
