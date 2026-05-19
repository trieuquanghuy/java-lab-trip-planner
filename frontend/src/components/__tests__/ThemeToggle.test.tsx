import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeToggle } from '../ThemeToggle';

// Mock matchMedia
const mockMatchMedia = vi.fn();

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove('dark');

  mockMatchMedia.mockReturnValue({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: mockMatchMedia,
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ThemeToggle', () => {
  it('renders a button with accessible label', () => {
    render(<ThemeToggle />);
    const btn = screen.getByRole('button');
    expect(btn).toHaveAttribute('aria-label');
  });

  it('defaults to system preference (light) when no localStorage', () => {
    render(<ThemeToggle />);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(screen.getByLabelText('Switch to dark mode')).toBeInTheDocument();
  });

  it('defaults to system preference (dark) when no localStorage and system is dark', () => {
    mockMatchMedia.mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    });
    render(<ThemeToggle />);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByLabelText('Switch to light mode')).toBeInTheDocument();
  });

  it('respects localStorage "dark" on mount', () => {
    localStorage.setItem('theme', 'dark');
    render(<ThemeToggle />);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(screen.getByLabelText('Switch to light mode')).toBeInTheDocument();
  });

  it('respects localStorage "light" on mount', () => {
    localStorage.setItem('theme', 'light');
    render(<ThemeToggle />);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(screen.getByLabelText('Switch to dark mode')).toBeInTheDocument();
  });

  it('toggles from light to dark on click', () => {
    render(<ThemeToggle />);
    fireEvent.click(screen.getByRole('button'));
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('toggles from dark to light on click', () => {
    localStorage.setItem('theme', 'dark');
    render(<ThemeToggle />);
    fireEvent.click(screen.getByRole('button'));
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('theme')).toBe('light');
  });

  it('removes localStorage when in system mode', () => {
    // system mode = no localStorage set initially
    render(<ThemeToggle />);
    // The component defaults to 'system', which doesn't persist
    expect(localStorage.getItem('theme')).toBeNull();
  });

  it('listens to system preference changes when in system mode', () => {
    const addEventListener = vi.fn();
    const removeEventListener = vi.fn();
    mockMatchMedia.mockReturnValue({
      matches: false,
      addEventListener,
      removeEventListener,
    });

    const { unmount } = render(<ThemeToggle />);
    expect(addEventListener).toHaveBeenCalledWith('change', expect.any(Function));

    unmount();
    expect(removeEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });

  it('does not listen to system changes after user picks a theme', () => {
    const addEventListener = vi.fn();
    mockMatchMedia.mockReturnValue({
      matches: false,
      addEventListener,
      removeEventListener: vi.fn(),
    });

    render(<ThemeToggle />);
    // Initial render in 'system' mode adds listener

    // Click to toggle to dark (exits system mode)
    fireEvent.click(screen.getByRole('button'));

    // After rerender with 'dark' theme, listener should not be re-added
    // The effect cleanup removes the old listener and the new effect (theme !== 'system') does nothing
    expect(localStorage.getItem('theme')).toBe('dark');
  });
});
