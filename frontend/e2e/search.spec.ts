import { test, expect } from '@playwright/test';

test.describe('Destination Search (F1)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('homepage renders search input', async ({ page }) => {
    await expect(page.getByRole('textbox', { name: /search|city|country/i })).toBeVisible();
  });

  test('search input accepts text input', async ({ page }) => {
    const searchInput = page.getByRole('textbox', { name: /search|city|country/i });
    await searchInput.fill('Paris');
    await expect(searchInput).toHaveValue('Paris');
  });

  test('destination cards appear after searching', async ({ page }) => {
    // Mock the city search API — shape: { items: [CitySearchItem] }
    await page.route('**/api/search*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { name: 'Paris', country: 'France', lat: 48.8566, lng: 2.3522, type: 'city' },
          ],
        }),
      });
    });

    await page.route('**/api/destinations*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { providerRef: 'otm:eiffel', name: 'Eiffel Tower', category: 'Landmark', lat: 48.8584, lng: 2.2945, rating: 4.8, photoUrl: null },
            { providerRef: 'otm:louvre', name: 'Louvre Museum', category: 'Museum', lat: 48.8606, lng: 2.3376, rating: 4.7, photoUrl: null },
          ],
          providerStatus: 'ok',
        }),
      });
    });

    const searchInput = page.getByRole('textbox', { name: /search|city|country/i });
    await searchInput.fill('Paris');
    // Click the city suggestion from the dropdown to trigger fetchNearby
    await expect(page.getByText('Paris, France')).toBeVisible({ timeout: 5000 });
    await page.getByText('Paris, France').click();
    await expect(page.getByText('Eiffel Tower')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Louvre Museum')).toBeVisible();
  });

  test('clicking a destination navigates to its detail page', async ({ page }) => {
    await page.route('**/api/search*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [{ name: 'Paris', country: 'France', lat: 48.8566, lng: 2.3522, type: 'city' }],
        }),
      });
    });

    await page.route('**/api/destinations*', async (route) => {
      const url = route.request().url();
      if (url.includes('otm%3Aeiffel') || url.includes('/otm:eiffel')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            providerRef: 'otm:eiffel', name: 'Eiffel Tower', category: 'Landmark',
            lat: 48.8584, lng: 2.2945, rating: 4.8, photos: [], openingHours: null,
            address: null, website: null, shortDescription: null, isFavorite: false, updatedAt: '',
          }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            items: [{ providerRef: 'otm:eiffel', name: 'Eiffel Tower', category: 'Landmark', lat: 48.8584, lng: 2.2945, rating: 4.8, photoUrl: null }],
            providerStatus: 'ok',
          }),
        });
      }
    });

    const searchInput = page.getByRole('textbox', { name: /search|city|country/i });
    await searchInput.fill('Paris');
    await expect(page.getByText('Paris, France')).toBeVisible({ timeout: 5000 });
    await page.getByText('Paris, France').click();
    await expect(page.getByText('Eiffel Tower')).toBeVisible({ timeout: 5000 });
    await page.getByText('Eiffel Tower').first().click();
    await expect(page).toHaveURL(/destinations\/otm:eiffel/, { timeout: 5000 });
  });
});
