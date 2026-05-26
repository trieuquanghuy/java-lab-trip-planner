package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.exception.InvalidDateRangeException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripService {

    private final TripRepository tripRepo;
    private final ItineraryDayRepository dayRepo;
    private final ItineraryItemRepository itemRepo;
    private final DayMaterializationService dayMaterializationService;

    public TripService(TripRepository tripRepo,
                       ItineraryDayRepository dayRepo,
                       ItineraryItemRepository itemRepo,
                       DayMaterializationService dayMaterializationService) {
        this.tripRepo = tripRepo;
        this.dayRepo = dayRepo;
        this.itemRepo = itemRepo;
        this.dayMaterializationService = dayMaterializationService;
    }

    /**
     * Create a new trip. If dates are provided, materializes days immediately.
     * Per D-08: returns the trip (controller maps to 201 + Location header).
     * Per D-13: date validation — endDate >= startDate when both set.
     */
    @Transactional
    public TripWithDays create(String userId, String name, LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        Trip trip = new Trip(UUID.randomUUID(), UUID.fromString(userId), name.trim(), startDate, endDate);
        tripRepo.save(trip);

        List<ItineraryDay> days = List.of();
        if (startDate != null && endDate != null) {
            days = dayMaterializationService.materializeDays(trip, startDate, endDate, false);
        }
        return new TripWithDays(trip, days, Map.of(), null);
    }

    /**
     * Get a single trip with its days.
     * Per D-09: findByIdAndUserId returns empty Optional → TripNotFoundException → 404.
     */
    @Transactional(readOnly = true)
    public TripWithDays findTrip(UUID tripId, String userId) {
        Trip trip = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
        List<ItineraryDay> days = dayRepo.findByTripIdOrderByDayIndex(trip.getId());

        // Load items per day
        Map<UUID, List<ItineraryItem>> itemsByDay = new HashMap<>();
        List<UUID> dayIds = days.stream().map(ItineraryDay::getId).toList();
        for (ItineraryDay day : days) {
            itemsByDay.put(day.getId(), itemRepo.findByItineraryDayIdOrderByPositionAsc(day.getId()));
        }

        // Cover image fallback: first item with photo_url (D-09/D-10)
        String resolvedCoverImage = null;
        if (trip.getCoverImageUrl() == null && !dayIds.isEmpty()) {
            List<ItineraryItem> withPhoto = itemRepo.findItemsWithPhotoByDayIds(dayIds);
            if (!withPhoto.isEmpty()) {
                resolvedCoverImage = withPhoto.get(0).getPhotoUrl();
            }
        }

        return new TripWithDays(trip, days, itemsByDay, resolvedCoverImage);
    }

    /**
     * List trips for a user (paginated).
     * Per D-07: returns Page<Trip> — controller maps to TripListResponse shape.
     * Per D-11: empty list returns content: [], totalElements: 0.
     */
    @Transactional(readOnly = true)
    public Page<Trip> listTrips(String userId, Pageable pageable) {
        return tripRepo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId), pageable);
    }

    /**
     * Partial update of a trip. Date changes trigger day materialization.
     * Per D-06: confirmShorten query param for shrink-conflict guard.
     * Per D-13: date validation — endDate >= startDate when both set.
     */
    @Transactional
    public TripWithDays updateTrip(UUID tripId, String userId, String name,
                                   LocalDate startDate, LocalDate endDate,
                                   String coverImageUrl, boolean confirmShorten) {
        Trip trip = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);

        // Apply partial updates — null means "no change"
        if (name != null) {
            trip.setName(name.trim());
        }
        if (coverImageUrl != null) {
            trip.setCoverImageUrl(coverImageUrl);
        }

        // Date handling — determine effective dates after update
        boolean datesChanged = false;
        if (startDate != null) {
            trip.setStartDate(startDate);
            datesChanged = true;
        }
        if (endDate != null) {
            trip.setEndDate(endDate);
            datesChanged = true;
        }

        // Validate effective dates
        validateDates(trip.getStartDate(), trip.getEndDate());

        tripRepo.save(trip);

        // Re-materialize days if dates changed
        List<ItineraryDay> days;
        if (datesChanged && trip.getStartDate() != null && trip.getEndDate() != null) {
            days = dayMaterializationService.materializeDays(trip, trip.getStartDate(),
                    trip.getEndDate(), confirmShorten);
        } else {
            days = dayRepo.findByTripIdOrderByDayIndex(trip.getId());
        }

        return new TripWithDays(trip, days, Map.of(), null);
    }

    /**
     * Delete a trip. Owner only — 404 if not found.
     * DB ON DELETE CASCADE handles days and items.
     */
    @Transactional
    public void deleteTrip(UUID tripId, String userId) {
        Trip trip = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
        tripRepo.delete(trip);
    }

    /**
     * Duplicate an existing trip with all days and items.
     * Per D-06: full deep copy (days, items, time slots, notes, cover image).
     * Per D-08: name = "Copy of {original}" truncated to 120 chars.
     * Per D-09: dates are null on the duplicate.
     */
    @Transactional
    public TripWithDays duplicateTrip(UUID tripId, String userId) {
        Trip source = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);

        String newName = "Copy of " + source.getName();
        if (newName.length() > 120) {
            newName = newName.substring(0, 120);
        }
        Trip duplicate = new Trip(UUID.randomUUID(), UUID.fromString(userId), newName, null, null);
        duplicate.setCoverImageUrl(source.getCoverImageUrl());
        tripRepo.save(duplicate);

        List<ItineraryDay> sourceDays = dayRepo.findByTripIdOrderByDayIndex(source.getId());
        List<ItineraryDay> newDays = new ArrayList<>();
        Map<UUID, List<ItineraryItem>> itemsByDay = new HashMap<>();

        for (ItineraryDay srcDay : sourceDays) {
            ItineraryDay newDay = new ItineraryDay(UUID.randomUUID(), duplicate.getId(),
                    srcDay.getDayDate(), srcDay.getDayIndex());
            newDays.add(newDay);

            List<ItineraryItem> srcItems = itemRepo.findByItineraryDayIdOrderByPositionAsc(srcDay.getId());
            List<ItineraryItem> newItems = new ArrayList<>();
            for (ItineraryItem srcItem : srcItems) {
                ItineraryItem newItem = new ItineraryItem(UUID.randomUUID(), newDay.getId(),
                        srcItem.getDestinationRef(), srcItem.getPosition(),
                        srcItem.getTimeSlot(), srcItem.getNote(), srcItem.getPhotoUrl());
                newItems.add(newItem);
            }
            itemRepo.saveAll(newItems);
            itemsByDay.put(newDay.getId(), newItems);
        }
        dayRepo.saveAll(newDays);

        return new TripWithDays(duplicate, newDays, itemsByDay, null);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidDateRangeException();
        }
    }

    /** Result container — trip plus its materialized days, items per day, and resolved cover image. */
    public record TripWithDays(Trip trip, List<ItineraryDay> days,
                               Map<UUID, List<ItineraryItem>> itemsByDay, String resolvedCoverImage) {}
}
