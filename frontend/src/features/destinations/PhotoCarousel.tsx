import { useState, useRef, useCallback, useEffect } from 'react';
import { MapPin } from 'lucide-react';

interface PhotoCarouselProps {
  photos: string[];
}

export function PhotoCarousel({ photos }: PhotoCarouselProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleScroll = useCallback(() => {
    if (!scrollRef.current) return;
    const { scrollLeft, clientWidth } = scrollRef.current;
    const index = Math.round(scrollLeft / clientWidth);
    setCurrentIndex(index);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  if (photos.length === 0) {
    return (
      <div className="h-64 w-full bg-gradient-to-br from-blue-400 to-purple-500 rounded-lg flex flex-col items-center justify-center gap-2">
        <MapPin className="h-12 w-12 text-white" />
        <span className="text-white/80 text-sm">No photos available</span>
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden rounded-lg">
      <div
        ref={scrollRef}
        className="flex overflow-x-auto snap-x snap-mandatory scrollbar-hide"
        style={{ WebkitOverflowScrolling: 'touch' }}
      >
        {photos.map((url, idx) => (
          <img
            key={idx}
            src={url}
            alt={`Destination photo ${idx + 1}`}
            loading="lazy"
            className="w-full h-64 object-cover flex-shrink-0 snap-center"
          />
        ))}
      </div>
      {photos.length > 1 && (
        <div className="absolute bottom-2 left-1/2 -translate-x-1/2 flex gap-1.5">
          {photos.map((_, idx) => (
            <span
              key={idx}
              className={`w-2 h-2 rounded-full ${idx === currentIndex ? 'bg-white' : 'bg-white/50'}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
