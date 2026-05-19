import { useState, useRef, useCallback, useEffect } from 'react';
import { MapPin, ChevronLeft, ChevronRight } from 'lucide-react';

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

  const scrollTo = (index: number) => {
    if (!scrollRef.current) return;
    const { clientWidth } = scrollRef.current;
    scrollRef.current.scrollTo({ left: clientWidth * index, behavior: 'smooth' });
  };

  if (photos.length === 0) {
    return (
      <div className="h-64 w-full bg-gradient-to-br from-blue-400 to-purple-500 rounded-lg flex flex-col items-center justify-center gap-2">
        <MapPin className="h-12 w-12 text-white" aria-hidden="true" />
        <span className="text-white/80 text-sm">No photos available</span>
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden rounded-lg group">
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
            className="w-full h-64 object-cover flex-shrink-0 snap-center transition-opacity duration-300"
          />
        ))}
      </div>

      {/* Navigation arrows */}
      {photos.length > 1 && (
        <>
          <button
            onClick={() => scrollTo(Math.max(0, currentIndex - 1))}
            className="absolute left-2 top-1/2 -translate-y-1/2 h-8 w-8 rounded-full bg-black/40 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:bg-black/60 disabled:hidden"
            disabled={currentIndex === 0}
            aria-label="Previous photo"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={() => scrollTo(Math.min(photos.length - 1, currentIndex + 1))}
            className="absolute right-2 top-1/2 -translate-y-1/2 h-8 w-8 rounded-full bg-black/40 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:bg-black/60 disabled:hidden"
            disabled={currentIndex === photos.length - 1}
            aria-label="Next photo"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </>
      )}

      {/* Dot indicators */}
      {photos.length > 1 && (
        <div className="absolute bottom-2 left-1/2 -translate-x-1/2 flex gap-1.5">
          {photos.map((_, idx) => (
            <button
              key={idx}
              onClick={() => scrollTo(idx)}
              aria-label={`Go to photo ${idx + 1}`}
              className={`w-2 h-2 rounded-full transition-all duration-300 ${
                idx === currentIndex ? 'bg-white w-4' : 'bg-white/50 hover:bg-white/75'
              }`}
            />
          ))}
        </div>
      )}

      {/* Photo counter */}
      <div className="absolute top-2 right-2 bg-black/50 text-white text-xs px-2 py-1 rounded-full">
        {currentIndex + 1} / {photos.length}
      </div>
    </div>
  );
}
