package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.service.exception.ShortenConflictException;
import com.tripplanner.trip.service.exception.ShortenConflictException.OrphanedDayInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DayMaterializationService {

    private final ItineraryDayRepository dayRepo;
    private final ItineraryItemRepository itemRepo;

    public DayMaterializationService(ItineraryDayRepository dayRepo,
                                     ItineraryItemRepository itemRepo) {
        this.dayRepo = dayRepo;
        this.itemRepo = itemRepo;
    }

    /**
     * Idempotent day materialization per D-01.
     * Separate bean ensures @Transactional proxy interception from TripService.
     *
     * Algorithm:
     * 1. Compute desired day set from newStart..newEnd
     * 2. Load existing days for this trip
     * 3. Determine days to add and days to remove
     * 4. If removing days with items → throw ShortenConflictException (unless confirmShorten)
     * 5. DELETE orphaned items (D-02: explicit SQL, not JPA cascade)
     * 6. DELETE removed days
     * 7. Re-index remaining days based on new start date
     * 8. INSERT new days
     * 9. Return final day list ordered by dayIndex
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<ItineraryDay> materializeDays(Trip trip, LocalDate newStart, LocalDate newEnd,
                                              boolean confirmShorten) {
        // 1. Compute desired dates
        Set<LocalDate> desiredDates = newStart.datesUntil(newEnd.plusDays(1))
                .collect(Collectors.toSet());

        // 2. Load existing days
        List<ItineraryDay> existingDays = dayRepo.findByTripIdOrderByDayIndex(trip.getId());
        Map<LocalDate, ItineraryDay> existingByDate = existingDays.stream()
                .collect(Collectors.toMap(ItineraryDay::getDayDate, d -> d));

        // 3. Determine adds and removes
        Set<LocalDate> existingDates = existingByDate.keySet();
        List<LocalDate> datesToAdd = desiredDates.stream()
                .filter(d -> !existingDates.contains(d))
                .sorted()
                .toList();
        List<ItineraryDay> daysToRemove = existingDays.stream()
                .filter(d -> !desiredDates.contains(d.getDayDate()))
                .toList();

        // 4. Check for orphaned items on days being removed (D-03)
        if (!daysToRemove.isEmpty()) {
            List<UUID> dayIdsToRemove = daysToRemove.stream()
                    .map(ItineraryDay::getId)
                    .toList();
            long itemCount = itemRepo.countByDayIds(dayIdsToRemove);

            if (itemCount > 0 && !confirmShorten) {
                // Build orphan info per day for the 409 response body (D-06)
                List<OrphanedDayInfo> orphanInfo = daysToRemove.stream()
                        .map(d -> {
                            long count = itemRepo.countByDayIds(List.of(d.getId()));
                            return new OrphanedDayInfo(d.getDayDate(), d.getDayIndex(), count);
                        })
                        .filter(info -> info.itemCount() > 0)
                        .toList();
                throw new ShortenConflictException(orphanInfo);
            }

            // 5. DELETE orphaned items (D-02: explicit SQL DELETE)
            if (itemCount > 0) {
                itemRepo.deleteByDayIds(dayIdsToRemove);
            }

            // 6. DELETE removed days
            dayRepo.deleteByTripIdAndIdIn(trip.getId(), dayIdsToRemove);
        }

        // 7. Re-index remaining days based on new start date
        List<ItineraryDay> remainingDays = dayRepo.findByTripIdOrderByDayIndex(trip.getId());
        for (ItineraryDay day : remainingDays) {
            int correctIndex = (int) ChronoUnit.DAYS.between(newStart, day.getDayDate()) + 1;
            if (correctIndex != day.getDayIndex()) {
                dayRepo.updateDayIndex(day.getId(), correctIndex);
            }
        }

        // 8. INSERT new days with correct dayIndex
        List<ItineraryDay> newDays = new ArrayList<>();
        for (LocalDate date : datesToAdd) {
            int dayIndex = (int) ChronoUnit.DAYS.between(newStart, date) + 1;
            newDays.add(new ItineraryDay(UUID.randomUUID(), trip.getId(), date, dayIndex));
        }
        if (!newDays.isEmpty()) {
            dayRepo.saveAll(newDays);
        }

        // 9. Return final ordered list
        return dayRepo.findByTripIdOrderByDayIndex(trip.getId());
    }
}
