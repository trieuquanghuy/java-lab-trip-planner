import { test, expect } from '@playwright/test';

const travelResponse = {
  segments: [
    { durationMinutes: 12.5, distanceKm: 3.2 },
  ],
};

const unavailableResponse = {
  segments: [
    { durationMinutes: null, distanceKm: null },
  ],
};

const eiffelDetail = {
  providerRef: 'otm:eiffel',
  name: 'Eiffel Tower',
  category: 'Landmark',
  shortDescription: null,
  rating: 4.8,
  lat: 48.8584,
  lng: 2.2945,
  address: 'Champ de Mars',
  website: null,
  photos: [],
  openingHours: null,
  isFavorite: false,
  updatedAt: '',
};

const louvreDetail = {
  providerRef: 'otm:louvre',
  name: 'Louvre Museum',
  category: 'Museum',
  shortDescription: null,
  rating: 4.7,
  lat: 48.8606,
  lng: 2.3376,
  address: 'Rue de Rivoli',
  website: null,
  photos: [],
  openingHours: null,
  isFavorite: false,
  updatedAt: '',
};

const tripWithItems = {
  id: 'trip-travel',
  name: 'Paris 3 Days',
  startDate: '2026-10-01',
  endDate: '2026-10-03',
  coverImageUrl: null,
  createdAt: '',
  updatedAt: '',
  shareToken: 'travel-share-token',
  shareEnabled: true,
  days: [
    {
      id: 'day-1',
      dayDate: '2026-10-01',
      dayIndex: 1,
      items: [
        { id: 'item-1', itineraryDayId: 'day-1', destinationRef: 'otm:eiffel', position: 0, timeSlot: '09:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
        { id: 'item-2', itineraryDayId: 'day-1', destinationRef: 'otm:louvre', position: 1, timeSlot: '14:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
      ],
    },
    { id: 'day-2', dayDate: '2026-10-02', dayIndex: 2, items: [] },
    { id: 'day-3', dayDate: '2026-10-03', dayIndex: 3, items: [] },
  ],
};

const tripWithOneItem = {
  ...tripWithItems,
  shareToken: 'single-item-token',
  days: [
    {
      id: 'day-1',
      dayDate: '2026-10-01',
      dayIndex: 1,
      items: [
        { id: 'item-1', itineraryDayId: 'day-1', destinationRef: 'otm:eiffel', position: 0, timeSlot: '09:00', note: null, photoUrl: null, createdAt: '', updatedAt: '' },
      ],
    },
    { id: 'day-2', dayDate: '2026-10-02', dayIndex: 2, items: [] },
    { id: 'day-3', dayDate: '2026-10-03', dayIndex: 3, items: [] },
  ],
};

test.describe('Travel Time & Distance (Phase 15)', () => {
  // Shared route setup used across tests
  async function setupCommonRoutes(page: import('@playwright/test').Page) {
    // Use a single wildcard handler to match URL-encoded providerRefs
    // (otm:eiffel → otm%3Aeiffel, otm:louvre → otm%3Alouvre)
    await page.route('**/api/destinations/**', async (route) => {
      const url = route.request().url();
      if (url.includes('otm%3Alouvre') || url.includes('otm:louvre')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(louvreDetail),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(eiffelDetail),
        });
      }
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ days: [] }),
      });
    });
  }

  test('travel segment shows between consecutive items', async ({ page }) => {
    await page.route('**/api/share/travel-share-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(tripWithItems),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(travelResponse),
      });
    });

    await setupCommonRoutes(page);
    await page.goto('/share/travel-share-token');

    // Car emoji from TravelSegment component
    await expect(page.getByText('🚗').first()).toBeVisible({ timeout: 5000 });
    // Duration: 12.5 min rounds to 13 min (or shown as "12.5 min")
    await expect(page.getByText(/1[23]\s*min/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('travel segment shows unavailable when API returns null values', async ({ page }) => {
    await page.route('**/api/share/travel-share-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(tripWithItems),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(unavailableResponse),
      });
    });

    await setupCommonRoutes(page);
    await page.goto('/share/travel-share-token');

    await expect(page.getByText(/travel time unavailable/i)).toBeVisible({ timeout: 5000 });
  });

  test('travel segment not shown when only one item in day', async ({ page }) => {
    await page.route('**/api/share/single-item-token', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(tripWithOneItem),
      });
    });

    await page.route('**/api/travel/segments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ segments: [] }),
      });
    });

    await setupCommonRoutes(page);
    await page.goto('/share/single-item-token');

    // Trip loads but no travel segment car emoji present
    await expect(page.getByRole('heading', { name: 'Paris 3 Days' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('🚗')).not.toBeVisible();
  });
});
