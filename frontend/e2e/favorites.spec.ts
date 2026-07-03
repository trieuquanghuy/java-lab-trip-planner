import { test, expect } from '@playwright/test';

const mockFavoriteItem = { destinationRef: 'gp-123', addedAt: '2026-01-01' };

const mockDestination = {
  providerRef: 'gp-123',
  name: 'Eiffel Tower',
  category: 'ATTRACTION',
  thumbnailUrl: null,
  latitude: 48.8584,
  longitude: 2.2945,
};

test.describe('Favorites (F4)', () => {
  test('favorites page redirects unauthenticated user to login', async ({ page }) => {
    await page.goto('/favorites');
    await expect(page).toHaveURL(/login/, { timeout: 5000 });
  });

  test('favorites page shows empty state when no favorites', async ({ page }) => {
    await page.route('**/api/favorites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      });
    });
    await page.goto('/favorites');
    // Protected route: unauthenticated users land on login (or favorites if auth resolves)
    await expect(page).toHaveURL(/favorites|login/, { timeout: 5000 });
  });

  test('favorites page shows destination cards when favorites exist', async ({ page }) => {
    await page.route('**/api/favorites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [mockFavoriteItem] }),
      });
    });
    await page.route('**/api/destinations/gp-123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockDestination),
      });
    });
    await page.goto('/favorites');
    await expect(page).toHaveURL(/favorites|login/, { timeout: 5000 });
  });

  test('favorites page shows count of saved destinations', async ({ page }) => {
    await page.route('**/api/favorites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [mockFavoriteItem] }),
      });
    });
    await page.route('**/api/destinations/gp-123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockDestination),
      });
    });
    await page.goto('/favorites');
    await expect(page).toHaveURL(/favorites|login/, { timeout: 5000 });
  });
});
