import { useDroppable } from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { ItineraryItemCard } from './ItineraryItemCard';
import { TravelSegment } from './TravelSegment';
import { WeatherCard } from './WeatherCard';
import { useTravel } from './travel.hooks';
import type { TripDay } from '@/types/trip';
import type { Waypoint } from '@/types/travel';
import type { DayWeather } from '@/types/weather';

interface Props {
  day: TripDay;
  tripId: string;
  waypoints?: Waypoint[];
  weather?: DayWeather | null;
}

export function DayColumn({ day, tripId, waypoints = [], weather }: Props) {
  const { setNodeRef, isOver } = useDroppable({
    id: day.id,
    data: { dayId: day.id },
  });

  const { data: travelData } = useTravel(waypoints);

  // Sort items: those with timeSlot first (chronologically), then by position
  const sortedItems = [...day.items].sort((a, b) => {
    if (a.timeSlot && b.timeSlot) return a.timeSlot.localeCompare(b.timeSlot);
    if (a.timeSlot && !b.timeSlot) return -1;
    if (!a.timeSlot && b.timeSlot) return 1;
    return a.position - b.position;
  });

  const dayLabel = day.dayDate
    ? new Date(day.dayDate + 'T00:00:00').toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
      })
    : `Day ${day.dayIndex + 1}`;

  return (
    <div
      ref={setNodeRef}
      className={`w-full lg:min-w-[300px] lg:w-[300px] flex-shrink-0 rounded-xl p-4 transition-colors ${
        isOver ? 'bg-primary/5 ring-2 ring-primary/30' : 'bg-muted/30'
      }`}
    >
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-sm">
          Day {day.dayIndex + 1} · {dayLabel}
        </h3>
        <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded-full">
          {day.items.length}
        </span>
      </div>
      {weather && (
        <div className="mb-3">
          <WeatherCard weather={weather} />
        </div>
      )}

      <SortableContext
        items={sortedItems.map((i) => i.id)}
        strategy={verticalListSortingStrategy}
      >
        <div className="space-y-2 min-h-[80px]">
          {sortedItems.length === 0 && (
            <div className="border-2 border-dashed rounded-lg p-4 text-center text-xs text-muted-foreground">
              Drop items here
            </div>
          )}
          {sortedItems.map((item, index) => (
            <div key={item.id}>
              {index > 0 && travelData?.segments[index - 1] && (
                <TravelSegment
                  durationMinutes={travelData.segments[index - 1].durationMinutes}
                  distanceKm={travelData.segments[index - 1].distanceKm}
                />
              )}
              <ItineraryItemCard item={item} tripId={tripId} />
            </div>
          ))}
        </div>
      </SortableContext>
    </div>
  );
}
