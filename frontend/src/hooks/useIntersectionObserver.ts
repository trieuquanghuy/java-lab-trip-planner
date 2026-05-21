import { useEffect, useRef } from 'react';

export function useIntersectionObserver(
  callback: () => void,
  options?: { enabled?: boolean; rootMargin?: string },
) {
  const ref = useRef<HTMLDivElement>(null);
  const { enabled = true, rootMargin = '200px' } = options ?? {};

  useEffect(() => {
    if (!enabled || !ref.current) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          callback();
        }
      },
      { rootMargin },
    );

    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [callback, enabled, rootMargin]);

  return ref;
}
