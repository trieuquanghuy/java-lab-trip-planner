export interface TripItem {
  id: string;
  itineraryDayId: string;
  destinationRef: string;
  position: number;
  timeSlot: string | null;
  note: string | null;
  photoUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TripDay {
  id: string;
  dayDate: string;
  dayIndex: number;
  items: TripItem[];
}

export interface Trip {
  id: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  coverImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
  days: TripDay[];
}

export interface TripSummary {
  id: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  coverImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TripListResponse {
  content: TripSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface CreateTripRequest {
  name: string;
  startDate?: string;
  endDate?: string;
}

export interface UpdateTripRequest {
  name?: string;
  startDate?: string;
  endDate?: string;
}

export interface CreateItemRequest {
  destinationRef: string;
  timeSlot?: string;
  note?: string;
  photoUrl?: string;
}

export interface UpdateItemRequest {
  position?: number;
  itineraryDayId?: string;
  timeSlot?: string | null;
  note?: string | null;
  photoUrl?: string | null;
}
