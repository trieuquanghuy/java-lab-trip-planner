export interface CitySearchItem {
  type: string;
  name: string;
  country: string;
  lat: number;
  lng: number;
}

export interface SearchResponse {
  items: CitySearchItem[];
}

export interface NearbyItem {
  providerRef: string;
  name: string;
  category: string | null;
  rating: number | null;
  photoUrl: string | null;
  lat: number;
  lng: number;
}

export interface ProviderStatus {
  openTripMap: string;
  foursquare: string;
}

export interface NearbyResponse {
  items: NearbyItem[];
  fromCache: boolean;
  providerStatus: ProviderStatus;
}

export interface DestinationDetailResponse {
  providerRef: string;
  name: string;
  category: string | null;
  shortDescription: string | null;
  rating: number | null;
  lat: number;
  lng: number;
  address: string | null;
  website: string | null;
  photos: string[];
  openingHours: Record<string, string> | null;
  fromCache: boolean;
  fetchedAt: string;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  code: string;
}

export interface BatchDestinationsResponse {
  items: NearbyItem[];
}
