package com.tripplanner.destination.weather;

import java.time.LocalDate;
import java.util.List;

public record WeatherResponse(List<DayWeather> days) {

    public record DayWeather(
            LocalDate date,
            Double tempMax,
            Double tempMin,
            Double precipitation,
            String icon,
            String description
    ) {}
}
