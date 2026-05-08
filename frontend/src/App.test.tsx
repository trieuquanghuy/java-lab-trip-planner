import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from './App';

describe('App', () => {
  let errorSpy: ReturnType<typeof vi.spyOn>;
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    errorSpy.mockRestore();
    warnSpy.mockRestore();
  });

  it('renders Trip Planner heading', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: /Trip Planner/i })).toBeInTheDocument();
  });

  it('emits zero console errors or warnings during StrictMode render (UI-SPEC §Copywriting Contract)', () => {
    // StrictMode double-invokes render to surface side effects; any React warning
    // (missing key, deprecated API, dangerous lifecycle) emits via console.error/warn.
    render(
      <React.StrictMode>
        <App />
      </React.StrictMode>
    );
    expect(errorSpy).not.toHaveBeenCalled();
    expect(warnSpy).not.toHaveBeenCalled();
  });
});
