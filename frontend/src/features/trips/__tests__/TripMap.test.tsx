import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TripMap, type MarkerData } from '@/features/trips/TripMap';

// ─── Mocks ──────────────────────────────────────────────────────────────────

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children, center, zoom }: { children: React.ReactNode; center: [number, number]; zoom: number }) => (
    <div data-testid="map-container" data-center={JSON.stringify(center)} data-zoom={zoom}>
      {children}
    </div>
  ),
  TileLayer: () => null,
  Marker: ({ children, position }: { children: React.ReactNode; position: [number, number] }) => (
    <div data-testid="marker" data-position={JSON.stringify(position)}>
      {children}
    </div>
  ),
  Popup: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="popup">{children}</div>
  ),
  useMap: () => ({ fitBounds: vi.fn() }),
}));

vi.mock('leaflet', () => ({
  default: {
    Icon: { Default: { prototype: {}, mergeOptions: vi.fn() } },
    latLngBounds: vi.fn(() => ({})),
  },
}));

// ─── Sample data ─────────────────────────────────────────────────────────────

const threeMarkers: MarkerData[] = [
  { id: '1', name: 'Eiffel Tower', lat: 48.8584, lng: 2.2945 },
  { id: '2', name: 'Louvre Museum', lat: 48.8606, lng: 2.3376 },
  { id: '3', name: 'Notre-Dame', lat: 48.853, lng: 2.3499 },
];

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('TripMap', () => {
  it('shows empty-state message when markers is empty', () => {
    render(<TripMap markers={[]} />);
    expect(screen.getByText(/add destinations to see them on the map/i)).toBeInTheDocument();
  });

  it('does NOT show empty-state message when markers are provided', () => {
    render(<TripMap markers={threeMarkers} />);
    expect(screen.queryByText(/add destinations to see them on the map/i)).not.toBeInTheDocument();
  });

  it('renders a Popup with the marker name for each marker', () => {
    render(<TripMap markers={threeMarkers} />);

    expect(screen.getByText('Eiffel Tower')).toBeInTheDocument();
    expect(screen.getByText('Louvre Museum')).toBeInTheDocument();
    expect(screen.getByText('Notre-Dame')).toBeInTheDocument();
  });

  it('renders the correct number of markers', () => {
    render(<TripMap markers={threeMarkers} />);

    expect(screen.getAllByTestId('marker')).toHaveLength(3);
    expect(screen.getAllByTestId('popup')).toHaveLength(3);
  });

  it('passes the average lat/lng as center to MapContainer', () => {
    render(<TripMap markers={threeMarkers} />);

    const expectedLat = (48.8584 + 48.8606 + 48.853) / 3;
    const expectedLng = (2.2945 + 2.3376 + 2.3499) / 3;

    const container = screen.getByTestId('map-container');
    const center = JSON.parse(container.getAttribute('data-center')!);

    expect(center[0]).toBeCloseTo(expectedLat, 5);
    expect(center[1]).toBeCloseTo(expectedLng, 5);
  });
});
