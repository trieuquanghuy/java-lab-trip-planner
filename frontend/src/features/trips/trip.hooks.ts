import {
  useQuery,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { tripApi } from './trip.api';
import type {
  CreateTripRequest,
  UpdateTripRequest,
  CreateItemRequest,
  UpdateItemRequest,
  TripListResponse,
} from '@/types/trip';

export const tripKeys = {
  all: ['trips'] as const,
  lists: () => [...tripKeys.all, 'list'] as const,
  list: (page: number) => [...tripKeys.lists(), page] as const,
  details: () => [...tripKeys.all, 'detail'] as const,
  detail: (id: string) => [...tripKeys.details(), id] as const,
};

export function useTrips(page = 0) {
  return useQuery({
    queryKey: tripKeys.list(page),
    queryFn: () => tripApi.list(page),
  });
}

export function useInfiniteTrips(size = 12) {
  return useInfiniteQuery<TripListResponse>({
    queryKey: [...tripKeys.lists(), 'infinite'],
    queryFn: ({ pageParam }) => tripApi.list(pageParam as number, size),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page < lastPage.totalPages - 1 ? lastPage.page + 1 : undefined,
  });
}

export function useTrip(id: string) {
  return useQuery({
    queryKey: tripKeys.detail(id),
    queryFn: () => tripApi.get(id),
    enabled: !!id,
  });
}

export function useCreateTrip() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTripRequest) => tripApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.lists() });
    },
  });
}

export function useUpdateTrip(tripId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateTripRequest) => tripApi.update(tripId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.detail(tripId) });
      queryClient.invalidateQueries({ queryKey: tripKeys.lists() });
    },
  });
}

export function useDeleteTrip() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tripId: string) => tripApi.delete(tripId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.lists() });
    },
  });
}

export function useAddItem(tripId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ dayId, data }: { dayId: string; data: CreateItemRequest }) =>
      tripApi.addItem(tripId, dayId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.detail(tripId) });
    },
  });
}

export function useUpdateItem(tripId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      itemId,
      data,
    }: {
      itemId: string;
      data: UpdateItemRequest;
    }) => tripApi.updateItem(tripId, itemId, data),
    onError: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.detail(tripId) });
    },
  });
}

export function useDeleteItem(tripId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (itemId: string) => tripApi.deleteItem(tripId, itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tripKeys.detail(tripId) });
    },
  });
}
