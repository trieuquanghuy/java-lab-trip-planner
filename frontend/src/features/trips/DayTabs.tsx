import type { TripDay } from '@/types/trip';

interface Props {
  days: TripDay[];
  activeDayId: string;
  onDayChange: (dayId: string) => void;
}

export function DayTabs({ days, activeDayId, onDayChange }: Props) {
  return (
    <div className="flex overflow-x-auto gap-1 pb-2 scrollbar-thin" role="tablist">
      {days.map((day) => (
        <button
          key={day.id}
          role="tab"
          aria-selected={day.id === activeDayId}
          onClick={() => onDayChange(day.id)}
          className={`flex-shrink-0 px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap ${
            day.id === activeDayId
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted hover:bg-muted/80 text-muted-foreground'
          }`}
        >
          Day {day.dayIndex + 1}
          {day.dayDate && (
            <span className="ml-1 opacity-75">
              {new Date(day.dayDate).toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
              })}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
