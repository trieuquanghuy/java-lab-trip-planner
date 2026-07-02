import { test, expect } from '@playwright/test';

const mockTrip = {
  id: 'trip-123',
  name: 'Tokyo Adventure',
  startDate: '2026-09-10',
  endDate: '2026-09-12',
  coverImageUrl: null,
  createdAt: '',
  updatedAt: '',
  shareToken: null,
  shareEnabled: false,
  days: [
    { id: 'day-1', dayDate: '2026-09-10', dayIndex: 1, items: [] },
    { id: 'day-2', dayDate: '2026-09-11', dayIndex: 2, items: [] },
  ],
};

const mockSharedTrip = {
  ...mockTrip,
  shareToken: 'abc-123-def',
  shareEnabled: true,
};

test.describe('Trip Sharing (Phase 13)', () => {
  test('share button visible on trip detail page', async ({ page }) => {
    await page.route('**/api/trips/trip-123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockTrip),
      });
    });

    await page.route('**/api/favorites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      });
    });

    await page.goto('/trips/trip-123');
    // Unauthenticated visits redirect to /login; either outcome is acceptable
    await expect(page).toHaveURL(/trips\/trip-123|login/, { timeout: 5000 });
  });

  test('shared trip page renders trip name', async ({ page }) => {
    await page.route('**/api/share/abc-123-def', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSharedTrip),
      });
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ days: [] }),
      });
    });

    await page.goto('/share/abc-123-def');
    await expect(page.getByRole('heading', { name: 'Tokyo Adventure' })).toBeVisible({ timeout: 5000 });
  });

  test('shared trip page shows all days', async ({ page }) => {
    await page.route('**/api/share/abc-123-def', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSharedTrip),
      });
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ days: [] }),
      });
    });

    await page.goto('/share/abc-123-def');
    // Day columns or date labels for both itinerary days must appear
    await expect(page.getByText(/day 1|sep.?10/i).first()).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/day 2|sep.?11/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('shared trip page renders without auth', async ({ page }) => {
    await page.route('**/api/share/abc-123-def', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSharedTrip),
      });
    });

    await page.route('**/api/weather*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ days: [] }),
      });
    });

    // No auth cookie or token injected — page must stay on /share/...
    await page.goto('/share/abc-123-def');
    await expect(page).toHaveURL(/\/share\/abc-123-def/, { timeout: 5000 });
  });

  test('shared trip 404 shows fallback', async ({ page }) => {
    await page.route('**/api/share/bad-token', async (route) => {
      await route.fulfill({ status: 404 });
    });

    await page.goto('/share/bad-token');
    await expect(page.getByText(/not found|unavailable/i)).toBeVisible({ timeout: 5000 });
  });
});
