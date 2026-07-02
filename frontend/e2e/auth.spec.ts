import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('signup page renders correctly', async ({ page }) => {
    await page.goto('/signup');
    await expect(page.getByRole('heading', { name: /sign up|create account/i })).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /sign up|create account/i })).toBeVisible();
  });

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /log in|sign in/i })).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /log in|sign in/i })).toBeVisible();
  });

  test('login shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('nonexistent@example.com');
    await page.getByLabel(/password/i).fill('wrongpassword');
    await page.getByRole('button', { name: /log in|sign in/i }).click();
    await expect(page.getByText(/invalid|incorrect|wrong|failed/i)).toBeVisible({ timeout: 5000 });
  });

  test('signup shows validation error for invalid email', async ({ page }) => {
    await page.goto('/signup');
    await page.getByLabel(/email/i).fill('not-an-email');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign up|create account/i }).click();
    await expect(page.getByText(/valid email|invalid email/i)).toBeVisible({ timeout: 3000 });
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
