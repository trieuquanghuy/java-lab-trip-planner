import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WeatherCard } from '../WeatherCard';
import type { DayWeather } from '@/types/weather';

const sunny: DayWeather = {
  date: '2026-07-15',
  tempMax: 28.7,
  tempMin: 16.2,
  precipitation: 0,
  icon: '☀️',
  description: 'Clear sky',
};

const rainy: DayWeather = {
  date: '2026-07-16',
  tempMax: 19.0,
  tempMin: 12.5,
  precipitation: 5.4,
  icon: '🌧️',
  description: 'Rain',
};

describe('WeatherCard', () => {
  it('renders nothing when weather is null', () => {
    const { container } = render(<WeatherCard weather={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders icon and temperature', () => {
    render(<WeatherCard weather={sunny} />);
    expect(screen.getByLabelText('Clear sky')).toBeInTheDocument();
    expect(screen.getByText('29°')).toBeInTheDocument();
    expect(screen.getByText('/ 16°')).toBeInTheDocument();
  });

  it('does not show precipitation badge when 0', () => {
    render(<WeatherCard weather={sunny} />);
    expect(screen.queryByText(/mm/)).not.toBeInTheDocument();
  });

  it('shows precipitation when above 0', () => {
    render(<WeatherCard weather={rainy} />);
    expect(screen.getByText('💧 5.4mm')).toBeInTheDocument();
  });

  it('rounds temperature values', () => {
    render(<WeatherCard weather={sunny} />);
    expect(screen.getByText('29°')).toBeInTheDocument();
    expect(screen.getByText('/ 16°')).toBeInTheDocument();
  });
});
