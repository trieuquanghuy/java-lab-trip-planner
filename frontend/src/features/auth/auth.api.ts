import { apiClient } from '@/lib/axios';
import type { LoginRequest, LoginResponse, SignupRequest, SignupResponse, RefreshResponse } from '@/types/auth';

export async function loginApi(data: LoginRequest): Promise<LoginResponse> {
  const res = await apiClient.post<LoginResponse>('/api/auth/login', data);
  return res.data;
}

export async function signupApi(data: SignupRequest): Promise<SignupResponse> {
  const res = await apiClient.post<SignupResponse>('/api/auth/signup', data);
  return res.data;
}

export async function refreshApi(): Promise<RefreshResponse> {
  const res = await apiClient.post<RefreshResponse>('/api/auth/refresh');
  return res.data;
}

export async function logoutApi(): Promise<void> {
  await apiClient.post('/api/auth/logout');
}
