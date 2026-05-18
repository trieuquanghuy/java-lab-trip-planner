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

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const activeItem = activeItemId
    ? days.flatMap((d) => d.items).find((i) => i.id === activeItemId)
    : null;

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
    >
      <div
        className="flex gap-4 overflow-x-auto pb-4 px-2"
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
