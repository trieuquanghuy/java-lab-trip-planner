import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical } from 'lucide-react';
import { useState } from 'react';
import { useUpdateItem } from './trip.hooks';
import type { TripItem } from '@/types/trip';

interface Props {
  item: TripItem;
  tripId: string;
  isDragging?: boolean;
}

export function ItineraryItemCard({ item, tripId, isDragging }: Props) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging: isSortableDragging,
  } = useSortable({
    id: item.id,
    data: { dayId: item.itineraryDayId },
  });

  const updateItem = useUpdateItem(tripId);
  const [timeValue, setTimeValue] = useState(item.timeSlot ?? '');
  const [noteValue, setNoteValue] = useState(item.note ?? '');

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const dragging = isDragging || isSortableDragging;

  const handleTimeBlur = () => {
    const newTime = timeValue || null;
    if (newTime !== item.timeSlot) {
      updateItem.mutate({
        itemId: item.id,
        data: { timeSlot: newTime },
      });
    }
  };

  const handleNoteBlur = () => {
    const newNote = noteValue || null;
    if (newNote !== item.note) {
      updateItem.mutate({
        itemId: item.id,
        data: { note: newNote },
      });
    }
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      data-dragging={dragging}
      className={`rounded-lg border bg-card p-3 shadow-sm hover:shadow-md transition-all focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
        dragging ? 'opacity-0' : ''
      }`}
      tabIndex={0}
      aria-label={`Itinerary item: ${item.destinationRef}`}
    >
      <div className="flex items-start gap-2">
        <button
          {...attributes}
          {...listeners}
          className="mt-1 cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground touch-none"
          aria-label="Drag to reorder"
        >
          <GripVertical className="w-4 h-4" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            {item.photoUrl && (
              <img
                src={item.photoUrl}
                alt=""
                className="w-10 h-10 rounded object-cover flex-shrink-0"
              />
            )}
            <div className="flex-1 min-w-0">
              <p className="font-medium text-sm truncate">
                {item.destinationRef}
              </p>
              <input
                type="time"
                value={timeValue}
                onChange={(e) => setTimeValue(e.target.value)}
                onBlur={handleTimeBlur}
                className="mt-1 text-xs px-2 py-0.5 border rounded bg-background w-24 focus:outline-none focus:ring-1 focus:ring-primary"
                aria-label="Set time slot"
              />
            </div>
          </div>
          <textarea
            value={noteValue}
            onChange={(e) => setNoteValue(e.target.value.slice(0, 500))}
            onBlur={handleNoteBlur}
            placeholder="Add a note..."
            rows={1}
            className="mt-2 w-full text-xs px-2 py-1 border rounded bg-background resize-none focus:outline-none focus:ring-1 focus:ring-primary"
          />
          {updateItem.isPending && (
            <span className="text-xs text-muted-foreground">Saving...</span>
          )}
        </div>
      </div>
    </div>
  );
}
