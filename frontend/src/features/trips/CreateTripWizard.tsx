import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X } from 'lucide-react';
import { useCreateTrip } from './trip.hooks';

interface Props {
  open: boolean;
  onClose: () => void;
}

export function CreateTripWizard({ open, onClose }: Props) {
  const [step, setStep] = useState(1);
  const [name, setName] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const navigate = useNavigate();
  const createTrip = useCreateTrip();

  if (!open) return null;

  const canNext = step === 1 ? name.trim().length > 0 : true;

  const handleCreate = () => {
    createTrip.mutate(
      {
        name: name.trim(),
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      },
      {
        onSuccess: (trip) => {
          onClose();
          resetForm();
          navigate(`/trips/${trip.id}`);
        },
      },
    );
  };

  const resetForm = () => {
    setStep(1);
    setName('');
    setStartDate('');
    setEndDate('');
  };

  const handleClose = () => {
    onClose();
    resetForm();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={handleClose}
      />
      <div className="relative bg-background rounded-xl shadow-2xl w-full max-w-md mx-4 p-6 animate-scale-in">
        <button
          onClick={handleClose}
          className="absolute top-4 right-4 text-muted-foreground hover:text-foreground"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Step indicator */}
        <div className="flex gap-2 mb-6">
          {[1, 2, 3].map((s) => (
            <div
              key={s}
              className={`h-1.5 flex-1 rounded-full transition-colors ${
                s <= step ? 'bg-primary' : 'bg-muted'
              }`}
            />
          ))}
        </div>

        {step === 1 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold">Name your trip</h2>
            <p className="text-sm text-muted-foreground">
              Give your adventure a memorable name.
            </p>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value.slice(0, 120))}
              placeholder="e.g., Tokyo 2026"
              className="w-full px-4 py-3 border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-primary"
              autoFocus
            />
            <p className="text-xs text-muted-foreground text-right">
              {name.length}/120
            </p>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold">When are you going?</h2>
            <p className="text-sm text-muted-foreground">
              Optional — you can set dates later.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-sm font-medium">Start date</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-primary mt-1"
                />
              </div>
              <div>
                <label className="text-sm font-medium">End date</label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  min={startDate}
                  className="w-full px-3 py-2 border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-primary mt-1"
                />
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <h2 className="text-xl font-semibold">Ready to plan!</h2>
            <div className="bg-muted/50 rounded-lg p-4 space-y-2">
              <p className="font-medium">{name}</p>
              {startDate && (
                <p className="text-sm text-muted-foreground">
                  {new Date(startDate).toLocaleDateString('en-US', {
                    month: 'long',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                  {endDate &&
                    ` – ${new Date(endDate).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}`}
                </p>
              )}
            </div>
          </div>
        )}

        {/* Navigation buttons */}
        <div className="flex justify-between mt-6">
          {step > 1 ? (
            <button
              onClick={() => setStep(step - 1)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              Back
            </button>
          ) : (
            <div />
          )}
          {step < 3 ? (
            <button
              onClick={() => setStep(step + 1)}
              disabled={!canNext}
              className="px-6 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              Next
            </button>
          ) : (
            <button
              onClick={handleCreate}
              disabled={createTrip.isPending}
              className="px-6 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {createTrip.isPending ? 'Creating...' : 'Create Trip'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
