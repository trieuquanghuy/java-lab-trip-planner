import { test, expect } from '@playwright/test';

const weatherResponse = {
  days: [
    { date: '2026-09-10', tempMax: 28.5, tempMin: 18.2, precipitation: 0, icon: '☀️', description: 'Clear sky' },
    { date: '2026-09-11', tempMax: 22.0, tempMin: 16.5, precipitation: 4.2, icon: '🌧️', description: 'Rain' },
  ],
};

const emptyWeatherResponse = { days: [] };

const sharedTrip = {
  id: 'weather-trip',
  name: 'Weather Test Trip',
  startDate: '2026-09-10',
  endDate: '2026-09-11',
  coverImageUrl: null,
  createdAt: '',
  updatedAt: '',
  shareToken: 'weather-share-token',
  shareEnabled: true,
  days: [
    {
      id: 'day-1',
      dayDate: '2026-09-10',
      dayIndex: 1,
      items: [
        {
          id: 'item-1',
          itineraryDayId: 'day-1',
          destinationRef: 'otm:weather-dest',
          position: 0,
          timeSlot: '10:00',
          note: null,
          photoUrl: null,
          createdAt: '',
          updatedAt: '',
        },
      ],
    },
    {
      id: 'day-2',
      dayDate: '2026-09-11',
      dayIndex: 2,
      items: [],
    },
  ],
};

const destinationDetail = {
  providerRef: 'otm:weather-dest',
  name: 'Tokyo Tower',
  category: 'Landmark',
  shortDescription: null,
  rating: 4.5,
  lat: 35.6586,
  lng: 139.7454,
  address: 'Tokyo',
  website: null,
  photos: [],
  openingHours: null,
  isFavorite: false,
  updatedAt: '',
};

test.describe('Weather Forecast (Phase 14)', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/share/weather-share-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(sharedTrip),
      });
    });

    // Use wildcard to match URL-encoded providerRef (otm:weather-dest → otm%3Aweather-dest)
    await page.route('**/api/destinations/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(destinationDetail),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ segments: [] }),
      });
    });
  });

  test('weather icons appear in day columns when data available', async ({ page }) => {
    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(weatherResponse),
      });
    });

    await page.goto('/share/weather-share-token');
    await expect(page.getByText('☀️').first()).toBeVisible({ timeout: 5000 });
    // Temperature shown as integer or one decimal (28 or 28.5)
    await expect(page.getByText(/28/).first()).toBeVisible({ timeout: 5000 });
  });

  test('precipitation badge shows when precipitation > 0', async ({ page }) => {
    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(weatherResponse),
      });
    });

    await page.goto('/share/weather-share-token');
    // Rainy day icon should be present (precipitation > 0 on day 2)
    await expect(page.getByText('🌧️').first()).toBeVisible({ timeout: 5000 });
  });

  test('weather gracefully absent when API returns empty', async ({ page }) => {
    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(emptyWeatherResponse),
      });
    });

    await page.goto('/share/weather-share-token');
    // Trip must still load without crashing
    await expect(page.getByRole('heading', { name: 'Weather Test Trip' })).toBeVisible({ timeout: 5000 });
    // No error state
    await expect(page.getByText(/error|failed/i)).not.toBeVisible();
  });
});
