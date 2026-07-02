// Helpers to set up and tear down test data via the API
const BASE_URL = process.env.API_URL ?? 'http://localhost:8180';

export async function registerUser(email: string, password: string): Promise<string> {
  const res = await fetch(`${BASE_URL}/api/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`Signup failed: ${res.status}`);
  const data = await res.json() as { userId: string };
  return data.userId;
}

export async function loginUser(email: string, password: string): Promise<string> {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  const data = await res.json() as { accessToken: string };
  return data.accessToken;
}

export async function createTrip(accessToken: string, name: string, startDate?: string, endDate?: string) {
  const res = await fetch(`${BASE_URL}/api/trips`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ name, startDate, endDate }),
  });
  if (!res.ok) throw new Error(`Create trip failed: ${res.status}`);
  return res.json();
}

export async function deleteTrip(accessToken: string, tripId: string) {
  await fetch(`${BASE_URL}/api/trips/${tripId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
