import { useCallback } from 'react';
import type { DragStartEvent, DragEndEvent, DragOverEvent } from '@dnd-kit/core';
import { arrayMove } from '@dnd-kit/sortable';
import { useDragStore } from './trip.store';
import { useUpdateItem } from './trip.hooks';
import type { TripDay } from '@/types/trip';

export function useDragDrop(tripId: string, serverDays: TripDay[]) {
  const { localDays, setLocalDays, startDrag, endDrag, resetLocal } =
    useDragStore();
  const updateItem = useUpdateItem(tripId);
  const days = localDays ?? serverDays;

  const handleDragStart = useCallback(
    (event: DragStartEvent) => {
      const itemId = event.active.id as string;
      const dayId = event.active.data.current?.dayId as string;
      startDrag(itemId, dayId);
      if (!localDays) setLocalDays(structuredClone(serverDays));
    },
    [serverDays, localDays, setLocalDays, startDrag],
  );

  const handleDragOver = useCallback(
    (event: DragOverEvent) => {
      const { active, over } = event;
      if (!over || !localDays) return;

      const activeId = active.id as string;
      const activeDayId = active.data.current?.dayId as string;
      const overDayId =
        (over.data.current?.dayId as string | undefined) ??
        (over.id as string);

      if (activeDayId === overDayId) return;

      const newDays = structuredClone(localDays);
      const sourceDay = newDays.find((d) => d.id === activeDayId);
      const targetDay = newDays.find((d) => d.id === overDayId);
      if (!sourceDay || !targetDay) return;

      const itemIndex = sourceDay.items.findIndex((i) => i.id === activeId);
      if (itemIndex === -1) return;

      const [movedItem] = sourceDay.items.splice(itemIndex, 1);
      movedItem.itineraryDayId = overDayId;
      targetDay.items.push(movedItem);

      active.data.current = { ...active.data.current, dayId: overDayId };
      setLocalDays(newDays);
    },
    [localDays, setLocalDays],
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      endDrag();

      if (!over || !localDays) {
        resetLocal();
        return;
      }

      const activeId = active.id as string;
      const overId = over.id as string;
      const dayId = active.data.current?.dayId as string;

      if (activeId === overId) {
        resetLocal();
        return;
      }

      const newDays = structuredClone(localDays);
      const day = newDays.find((d) => d.id === dayId);
      if (!day) {
        resetLocal();
        return;
      }

      const oldIndex = day.items.findIndex((i) => i.id === activeId);
      const newIndex = day.items.findIndex((i) => i.id === overId);
      if (oldIndex !== -1 && newIndex !== -1) {
        day.items = arrayMove(day.items, oldIndex, newIndex);
        day.items.forEach((item, idx) => {
          item.position = idx;
        });
      }

      setLocalDays(newDays);

      const movedItem = day.items.find((i) => i.id === activeId);
      if (movedItem) {
        updateItem.mutate(
          {
            itemId: activeId,
            data: { position: movedItem.position, itineraryDayId: dayId },
          },
          { onSettled: () => resetLocal() },
        );
      }
    },
    [localDays, endDrag, resetLocal, setLocalDays, updateItem],
  );

  return { days, handleDragStart, handleDragOver, handleDragEnd };
}
