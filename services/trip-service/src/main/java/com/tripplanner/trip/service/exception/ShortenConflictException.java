package com.tripplanner.trip.service.exception;

import java.time.LocalDate;
import java.util.List;

public class ShortenConflictException extends RuntimeException {

    public record OrphanedDayInfo(LocalDate dayDate, int dayIndex, long itemCount) {}

    private final List<OrphanedDayInfo> orphanedDays;

    public ShortenConflictException(List<OrphanedDayInfo> orphanedDays) {
        super();
        this.orphanedDays = orphanedDays;
    }

    public List<OrphanedDayInfo> getOrphanedDays() { return orphanedDays; }
}
