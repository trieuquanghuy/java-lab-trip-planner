import { MapPin, Plus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export function TripEmptyState() {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center animate-fade-in">
      <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
        <MapPin className="w-10 h-10 text-primary" />
      </div>
      <h3 className="text-xl font-semibold mb-2">Plan your adventure</h3>
      <p className="text-muted-foreground mb-6 max-w-sm">
        Search for destinations and add them to your trip to build your
        day-by-day itinerary.
      </p>
      <button
        onClick={() => navigate('/')}
        className="inline-flex items-center gap-2 px-6 py-3 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors"
      >
        <Plus className="w-4 h-4" />
        Add your first stop
      </button>
    </div>
  );
}
