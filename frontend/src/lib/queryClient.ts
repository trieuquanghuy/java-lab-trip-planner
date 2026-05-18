import { QueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if ((error as AxiosError)?.response?.status === 401) return false;
        return failureCount < 1;
      },
    },
  },
});
