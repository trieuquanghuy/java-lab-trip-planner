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
import { useDragDrop } from './useDragDrop';
import { useDragStore } from './trip.store';
import type { Trip } from '@/types/trip';

interface Props {
  trip: Trip;
}

export function ItineraryBoard({ trip }: Props) {
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
            <DayColumn day={mobileDay} tripId={trip.id} />
          </div>
        )}
      </div>

      {/* Desktop: horizontal scroll columns */}
      <div
        className="hidden lg:flex gap-4 overflow-x-auto pb-4 px-2"
        aria-live="polite"
      >
        {days.map((day) => (
          <DayColumn key={day.id} day={day} tripId={trip.id} />
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
