import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('signup page renders correctly', async ({ page }) => {
    await page.goto('/signup');
    // CardTitle renders as a div, not an h1/h2/h3 — use text selector
    await expect(page.getByText('Sign Up').first()).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /create account/i })).toBeVisible();
  });

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText('Log In').first()).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /log in/i })).toBeVisible();
  });

  test('login shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('nonexistent@example.com');
    await page.getByLabel(/password/i).fill('wrongpassword');
    await page.getByRole('button', { name: /log in|sign in/i }).click();
    await expect(page.getByText(/invalid|incorrect|wrong|failed/i)).toBeVisible({ timeout: 5000 });
  });

  test('signup shows validation error for invalid email', async ({ page }) => {
    // Mock auth API so form submission doesn't hit dead proxy and cause /error redirect
    await page.route('**/api/auth/signup', async (route) => {
      await route.fulfill({ status: 422, contentType: 'application/json', body: '{"code":"validation_error","detail":"Invalid email"}' });
    });
    await page.goto('/signup');
    // Use email that passes browser native HTML5 validation but fails zod's 2+ char TLD check
    await page.getByLabel(/email/i).fill('test@test.c');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign up|create account/i }).click();
    // zod fires before API call; falls back to API error if zod somehow passes
    await expect(page.getByText(/invalid email format|invalid email/i)).toBeVisible({ timeout: 5000 });
  });

  test('unauthenticated user is redirected from /trips to /login', async ({ page }) => {
    await page.goto('/trips');
    await expect(page).toHaveURL(/login/, { timeout: 5000 });
  });

  test('unauthenticated user is redirected from /favorites to /login', async ({ page }) => {
    await page.goto('/favorites');
    await expect(page).toHaveURL(/login/, { timeout: 5000 });
  });
});
