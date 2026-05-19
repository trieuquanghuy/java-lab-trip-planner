import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

export function RouteAnnouncer() {
  const location = useLocation();
  const announcerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (announcerRef.current) {
        announcerRef.current.textContent = `Navigated to ${document.title || location.pathname}`;
      }
    }, 100);
    return () => clearTimeout(timer);
  }, [location.pathname]);

  return (
    <div
      ref={announcerRef}
      role="status"
      aria-live="polite"
      aria-atomic="true"
      className="sr-only"
    />
  );
}
