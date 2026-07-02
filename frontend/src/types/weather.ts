export interface DayWeather {
  date: string;
  tempMax: number | null;
  tempMin: number | null;
  precipitation: number | null;
  icon: string;
  description: string;
}

export interface WeatherResponse {
  days: DayWeather[];
}
