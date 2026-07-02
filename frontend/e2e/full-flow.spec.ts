import { test, expect } from '@playwright/test';

const mockDestinations = {
  items: [
    { providerRef: 'otm:tokyo-tower', name: 'Tokyo Tower', category: 'Landmark', lat: 35.6586, lng: 139.7454, rating: 4.6, photoUrl: null },
    { providerRef: 'otm:senso-ji', name: 'Senso-ji Temple', category: 'Temple', lat: 35.7148, lng: 139.7967, rating: 4.7, photoUrl: null },
  ],
  providerStatus: 'ok',
};

const mockDestinationDetail = {
  providerRef: 'otm:tokyo-tower',
  name: 'Tokyo Tower',
  category: 'Landmark',
  shortDescription: 'Iconic lattice tower in the heart of Tokyo.',
  rating: 4.6,
  lat: 35.6586,
  lng: 139.7454,
  address: '4 Chome-2-8 Shibakoen, Minato City, Tokyo',
  website: 'https://www.tokyotower.co.jp',
  photos: [],
  openingHours: { Monday: '9:00 AM – 11:00 PM' },
  isFavorite: false,
  updatedAt: '2026-01-01T00:00:00Z',
};

const fullSharedTrip = {
  id: 'full-flow-trip',
  name: 'Tokyo Discovery',
  startDate: '2026-11-01',
  endDate: '2026-11-02',
  coverImageUrl: null,
  createdAt: '',
  updatedAt: '',
  shareToken: 'full-flow-token',
  shareEnabled: true,
  days: [
    {
      id: 'day-1',
      dayDate: '2026-11-01',
      dayIndex: 1,
      items: [
        { id: 'item-1', itineraryDayId: 'day-1', destinationRef: 'otm:tokyo-tower', position: 0, timeSlot: '10:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
        { id: 'item-2', itineraryDayId: 'day-1', destinationRef: 'otm:senso-ji', position: 1, timeSlot: '14:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
      ],
    },
    { id: 'day-2', dayDate: '2026-11-02', dayIndex: 2, items: [] },
  ],
};

const mockWeather = {
  days: [
    { date: '2026-11-01', tempMax: 18.0, tempMin: 12.0, precipitation: 0, icon: '🌤️', description: 'Partly cloudy' },
    { date: '2026-11-02', tempMax: 16.0, tempMin: 10.0, precipitation: 0, icon: '☀️', description: 'Clear' },
  ],
};

const sensoJiDetail = {
  providerRef: 'otm:senso-ji',
  name: 'Senso-ji Temple',
  category: 'Temple',
  shortDescription: null,
  rating: 4.7,
  lat: 35.7148,
  lng: 139.7967,
  address: 'Asakusa, Taito City, Tokyo',
  website: null,
  photos: [],
  openingHours: null,
  isFavorite: false,
  updatedAt: '',
};

const mockTravelSegments = {
  segments: [
    { durationMinutes: 25, distanceKm: 5.8 },
  ],
};

test.describe('Complete User Flow', () => {
  test('full journey: search → destination detail → view shared trip', async ({ page }) => {
    // ── 1. Homepage search ──────────────────────────────────────────────────
    await page.route('**/api/destinations*', async (route) => {
      const url = route.request().url();
      // providerRefs are URL-encoded by fetchDestinationDetail: otm:x → otm%3Ax
      if (url.includes('otm%3Atokyo-tower') || url.includes('/otm:tokyo-tower')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockDestinationDetail),
        });
      } else if (url.includes('otm%3Asenso-ji') || url.includes('/otm:senso-ji')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(sensoJiDetail),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockDestinations),
        });
      }
    });

    await page.goto('/');
    const searchInput = page.getByRole('textbox', { name: /search|city|country/i });
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('Tokyo');
    await expect(page.getByText('Tokyo Tower')).toBeVisible({ timeout: 5000 });

    // ── 2. Destination detail ───────────────────────────────────────────────
    await page.getByText('Tokyo Tower').click();
    await expect(page).toHaveURL(/destinations\/otm:tokyo-tower/, { timeout: 5000 });
    await expect(page.getByRole('heading', { name: 'Tokyo Tower' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Landmark')).toBeVisible();

    // ── 3. Shared trip page with full feature stack ─────────────────────────
    await page.route('**/api/share/full-flow-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(fullSharedTrip),
      });
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockWeather),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockTravelSegments),
      });
    });

    await page.goto('/share/full-flow-token');

    // Trip name and days
    await expect(page.getByRole('heading', { name: 'Tokyo Discovery' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/day 1|nov.?1/i).first()).toBeVisible({ timeout: 5000 });

    // Weather card
    await expect(page.getByText('🌤️').first()).toBeVisible({ timeout: 5000 });

    // Travel segment between the two items on day 1
    await expect(page.getByText('🚗').first()).toBeVisible({ timeout: 5000 });
  });

  test('protected routes redirect unauthenticated users', async ({ page }) => {
    // /trips and /favorites must redirect to /login
    await page.goto('/trips');
    await expect(page).toHaveURL(/login/, { timeout: 5000 });

    await page.goto('/favorites');
    await expect(page).toHaveURL(/login/, { timeout: 5000 });
  });

  test('shared trip route does not redirect unauthenticated users', async ({ page }) => {
    await page.route('**/api/share/full-flow-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(fullSharedTrip),
      });
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ days: [] }),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ segments: [] }),
      });
    });

    await page.route('**/api/destinations/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockDestinationDetail),
      });
    });

    // /share/:token must NOT redirect to /login — it is a public route
    await page.goto('/share/full-flow-token');
    await expect(page).toHaveURL(/\/share\/full-flow-token/, { timeout: 5000 });
  });
});
