import { useState } from 'react';
import {
  DndContext,
  DragOverlay,
  closestCorners,
  PointerSensor,
  KeyboardSensor,
  useSensors,
  useSensor,
} from '@dnd-kit/core';
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import { DayColumn } from './DayColumn';
import { DayTabs } from './DayTabs';
import { ItineraryItemCard } from './ItineraryItemCard';
import { TripEmptyState } from './TripEmptyState';
import { useDragDrop } from './useDragDrop';
import { useDragStore } from './trip.store';
import type { Trip } from '@/types/trip';
import type { Waypoint } from '@/types/travel';
import type { DayWeather } from '@/types/weather';

interface Props {
  trip: Trip;
  waypointsByDay?: Record<string, Waypoint[]>;
  weatherByDate?: Record<string, DayWeather>;
}

export function ItineraryBoard({ trip, waypointsByDay = {}, weatherByDate = {} }: Props) {
  const { days, handleDragStart, handleDragOver, handleDragEnd } = useDragDrop(
    trip.id,
    trip.days,
  );
  const activeItemId = useDragStore((s) => s.activeItemId);
  const [activeMobileDay, setActiveMobileDay] = useState(
    days[0]?.id ?? '',
  );

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const activeItem = activeItemId
    ? days.flatMap((d) => d.items).find((i) => i.id === activeItemId)
    : null;

  const mobileDay = days.find((d) => d.id === activeMobileDay) ?? days[0];

  const hasItems = days.some((d) => d.items.length > 0);
  if (!hasItems) return <TripEmptyState />;

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
    >
      {/* Mobile: tab-based single day view */}
      <div className="lg:hidden">
        <DayTabs
          days={days}
          activeDayId={activeMobileDay}
          onDayChange={setActiveMobileDay}
        />
        {mobileDay && (
          <div className="mt-3">
            <DayColumn
              day={mobileDay}
              tripId={trip.id}
              waypoints={waypointsByDay[mobileDay.id] ?? []}
              weather={weatherByDate[mobileDay.dayDate] ?? null}
            />
          </div>
        )}
      </div>

      {/* Desktop: horizontal scroll columns */}
      <div
        className="hidden lg:flex gap-4 overflow-x-auto pb-4 px-2"
        aria-live="polite"
      >
        {days.map((day) => (
          <DayColumn
            key={day.id}
            day={day}
            tripId={trip.id}
            waypoints={waypointsByDay[day.id] ?? []}
            weather={weatherByDate[day.dayDate] ?? null}
          />
        ))}
      </div>

      <DragOverlay>
        {activeItem ? (
          <div className="rotate-2 scale-105">
            <ItineraryItemCard
              item={activeItem}
              tripId={trip.id}
              isDragging={false}
            />
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
