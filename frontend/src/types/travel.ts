export interface Segment {
  durationMinutes: number | null;
  distanceKm: number | null;
}

export interface TravelResponse {
  segments: Segment[];
}

export interface Waypoint {
  lat: number;
  lng: number;
}
