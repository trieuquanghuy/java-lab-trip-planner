import { useState } from 'react';
import { Plus, ChevronDown } from 'lucide-react';
import { useTrips, useTrip, useAddItem } from '@/features/trips/trip.hooks';
import { useAuthStore } from '@/features/auth/auth.store';
import { Link } from 'react-router-dom';

interface Props {
  destinationRef: string;
  destinationName: string;
  photoUrl?: string;
}

export function AddToTripDropdown({
  destinationRef,
  destinationName,
  photoUrl,
}: Props) {
  const [open, setOpen] = useState(false);
  const [selectedTripId, setSelectedTripId] = useState('');
  const [selectedDayId, setSelectedDayId] = useState('');
  const [success, setSuccess] = useState(false);
  const { data: trips } = useTrips();
  const { data: selectedTrip } = useTrip(selectedTripId);
  const accessToken = useAuthStore((s) => s.accessToken);
  const addItem = useAddItem(selectedTripId);

  if (!accessToken) return null;

  const handleAdd = () => {
    if (!selectedTripId || !selectedDayId) return;
    addItem.mutate(
      {
        dayId: selectedDayId,
        data: {
          destinationRef,
          photoUrl,
        },
      },
      {
        onSuccess: () => {
          setSuccess(true);
          setTimeout(() => {
            setSuccess(false);
            setOpen(false);
            setSelectedTripId('');
            setSelectedDayId('');
          }, 1500);
        },
      },
    );
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors text-sm"
      >
        <Plus className="w-4 h-4" />
        Add to Trip
        <ChevronDown className="w-3 h-3" />
      </button>

      {open && (
        <div className="absolute top-full right-0 mt-2 w-72 bg-background border rounded-xl shadow-lg p-4 z-50 animate-scale-in">
          {success ? (
            <p className="text-sm text-green-600 font-medium text-center py-4">
              ✓ Added to trip!
            </p>
          ) : !trips || trips.content.length === 0 ? (
            <div className="text-center py-4">
              <p className="text-sm text-muted-foreground mb-2">
                No trips yet
              </p>
              <Link
                to="/trips"
                className="text-sm text-primary hover:underline"
              >
                Create a trip first
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm font-medium">
                Add "{destinationName}" to:
              </p>
              <select
                value={selectedTripId}
                onChange={(e) => {
                  setSelectedTripId(e.target.value);
                  setSelectedDayId('');
                }}
                className="w-full px-3 py-2 border rounded-lg bg-background text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Select a trip</option>
                {trips.content.map((trip) => (
                  <option key={trip.id} value={trip.id}>
                    {trip.name}
                  </option>
                ))}
              </select>

              {selectedTripId && selectedTrip && (
                <select
                  value={selectedDayId}
                  onChange={(e) => setSelectedDayId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg bg-background text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="">Select a day</option>
                  {selectedTrip.days.map((day) => (
                    <option key={day.id} value={day.id}>
                      Day {day.dayIndex + 1}
                      {day.dayDate ? ` — ${new Date(day.dayDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}` : ''}
                    </option>
                  ))}
                </select>
              )}

              <button
                onClick={handleAdd}
                disabled={!selectedTripId || !selectedDayId || addItem.isPending}
                className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-lg font-medium text-sm hover:bg-primary/90 disabled:opacity-50 transition-colors"
              >
                {addItem.isPending ? 'Adding...' : 'Add'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
