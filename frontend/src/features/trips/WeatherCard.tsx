import type { DayWeather } from '@/types/weather';

interface Props {
  weather: DayWeather | null | undefined;
}

export function WeatherCard({ weather }: Props) {
  if (!weather) return null;
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground bg-muted/50 rounded-lg px-3 py-2">
      <span className="text-xl" aria-label={weather.description}>
        {weather.icon}
      </span>
      <span className="font-medium">{Math.round(weather.tempMax ?? 0)}°</span>
      <span className="text-xs">/ {Math.round(weather.tempMin ?? 0)}°</span>
      {weather.precipitation != null && weather.precipitation > 0 && (
        <span className="text-xs text-blue-500">💧 {weather.precipitation}mm</span>
      )}
    </div>
  );
}
