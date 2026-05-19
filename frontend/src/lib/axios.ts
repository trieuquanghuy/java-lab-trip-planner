import axios from 'axios';
import { useAuthStore } from '@/features/auth/auth.store';
import { queryClient } from '@/lib/queryClient';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
});

// Request interceptor: attach access token + request ID
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Request-Id'] = crypto.randomUUID();
  return config;
});

// Response interceptor: Pitfall 9 — isRefreshing + failedQueue pattern
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((prom) => {
    if (token) {
      prom.resolve(token);
    } else {
      prom.reject(error);
    }
  });
  failedQueue = [];
}

apiClient.interceptors.response.use(undefined, async (error) => {
  const originalRequest = error.config;

  // Redirect to error page for 500+ server errors (excluding auth endpoints)
  if (error.response?.status >= 500) {
    const reqUrl = originalRequest?.url || '';
    if (!reqUrl.includes('/auth/') && globalThis.location.pathname !== '/error') {
      globalThis.location.href = '/error';
      throw error;
    }
  }

  if (error.response?.status !== 401 || originalRequest._retry) {
    throw error;
  }

  // NEVER retry auth endpoints — prevents infinite loop
  const url = originalRequest.url || '';
  if (url.includes('/auth/refresh') || url.includes('/auth/login')) {
    throw error;
  }

  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({
        resolve: (token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(apiClient(originalRequest));
        },
        reject: (err) => {
          reject(err);
        },
      });
    });
  }

  originalRequest._retry = true;
  isRefreshing = true;

  try {
    const { data } = await apiClient.post<{ accessToken: string; expiresIn: number }>(
      '/api/auth/refresh'
    );
    useAuthStore.getState().setSession(
      data.accessToken,
      useAuthStore.getState().user!
    );
    originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
    processQueue(null, data.accessToken);
    return apiClient(originalRequest);
  } catch (refreshError) {
    processQueue(refreshError, null);
    useAuthStore.getState().clearSession();
    queryClient.clear();
    throw refreshError;
  } finally {
    isRefreshing = false;
  }
});
