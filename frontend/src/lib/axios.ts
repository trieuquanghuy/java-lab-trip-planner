import axios from 'axios';

// Singleton apiClient. Phase 2 attaches Authorization header via the
// no-op request interceptor below; Phase 7 wires the 401 refresh flow.
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL, // e.g. http://localhost:8080
  withCredentials: true, // required for Phase 2 httpOnly cookie refresh-token flow
});

// Phase 0: no-op interceptor placeholder; just stamps an X-Request-Id so
// gateway-side observability (Phase 1+) can correlate requests end-to-end.
apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = crypto.randomUUID();
  return config;
});
