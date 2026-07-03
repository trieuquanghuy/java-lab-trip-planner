import { test, expect } from '@playwright/test';

test.describe('Destination Details (F2)', () => {
  const mockDestination = {
    providerRef: 'otm:eiffel123',
    name: 'Eiffel Tower',
    category: 'Landmark',
    shortDescription: 'Famous iron lattice tower on the Champ de Mars.',
    rating: 4.8,
    lat: 48.8584,
    lng: 2.2945,
    address: 'Champ de Mars, 5 Av. Anatole France, Paris',
    website: 'https://toureiffel.paris',
    photos: ['https://upload.wikimedia.org/eiffel.jpg'],
    openingHours: {
      'Monday': '9:00 AM – 11:45 PM',
      'Tuesday': '9:00 AM – 11:45 PM',
      'Wednesday': '9:00 AM – 11:45 PM',
    },
    isFavorite: false,
    updatedAt: '2026-01-01T00:00:00Z',
  };

  test.beforeEach(async ({ page }) => {
    // Use wildcard to match URL-encoded providerRef (otm:eiffel123 → otm%3Aeiffel123)
    await page.route('**/api/destinations/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockDestination),
      });
    });

    // Prevent auth refresh attempts from hanging or causing extra navigation
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
    });
  });

  test('destination detail page renders name and category', async ({ page }) => {
    await page.goto('/destinations/otm:eiffel123');
    await expect(page.getByRole('heading', { name: 'Eiffel Tower' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Landmark')).toBeVisible();
  });

  test('shows opening hours', async ({ page }) => {
    await page.goto('/destinations/otm:eiffel123');
    await expect(page.getByText('Monday')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('9:00 AM – 11:45 PM').first()).toBeVisible();
  });

  test('shows address', async ({ page }) => {
    await page.goto('/destinations/otm:eiffel123');
    await expect(page.getByText(/Champ de Mars/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('shows website link', async ({ page }) => {
    await page.goto('/destinations/otm:eiffel123');
    const link = page.getByRole('link', { name: /toureiffel\.paris/i });
    await expect(link).toBeVisible({ timeout: 5000 });
    await expect(link).toHaveAttribute('href', 'https://toureiffel.paris');
  });

  test('destination not found page shows fallback', async ({ page }) => {
    // Override the beforeEach wildcard to return 404 for this specific navigation
    await page.route('**/api/destinations/**', async (route) => {
      await route.fulfill({ status: 404 });
    });
    await page.goto('/destinations/otm-doesnotexist');
    await expect(page.getByText(/not found|unavailable/i)).toBeVisible({ timeout: 5000 });
  });

  test('shows "opening hours not available" when null', async ({ page }) => {
    // Override beforeEach wildcard for this specific navigation (no colon avoids URL-encoding issue)
    await page.route('**/api/destinations/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...mockDestination, providerRef: 'otm-no-hours', openingHours: null }),
      });
    });
    await page.goto('/destinations/otm-no-hours');
    await expect(page.getByText(/opening hours not available/i)).toBeVisible({ timeout: 5000 });
  });
});
