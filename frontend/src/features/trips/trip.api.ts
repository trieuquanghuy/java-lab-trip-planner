import { apiClient } from '@/lib/axios';
import type {
  Trip,
  TripListResponse,
  TripItem,
  CreateTripRequest,
  UpdateTripRequest,
  CreateItemRequest,
  UpdateItemRequest,
} from '@/types/trip';

export const tripApi = {
  list: (page = 0, size = 20) =>
    apiClient
      .get<TripListResponse>('/api/trips', { params: { page, size } })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<Trip>(`/api/trips/${id}`).then((r) => r.data),

  create: (data: CreateTripRequest) =>
    apiClient.post<Trip>('/api/trips', data).then((r) => r.data),

  update: (id: string, data: UpdateTripRequest, confirmShorten = false) =>
    apiClient
      .patch<Trip>(`/api/trips/${id}`, data, { params: { confirmShorten } })
      .then((r) => r.data),

  delete: (id: string) => apiClient.delete(`/api/trips/${id}`),

  addItem: (tripId: string, dayId: string, data: CreateItemRequest) =>
    apiClient
      .post<TripItem>(`/api/trips/${tripId}/days/${dayId}/items`, data)
      .then((r) => r.data),

  updateItem: (tripId: string, itemId: string, data: UpdateItemRequest) =>
    apiClient
      .patch<TripItem>(`/api/trips/${tripId}/items/${itemId}`, data)
      .then((r) => r.data),

  deleteItem: (tripId: string, itemId: string) =>
    apiClient.delete(`/api/trips/${tripId}/items/${itemId}`),
};
