package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.TripService.TripWithDays;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripShareService {

    private final TripRepository tripRepo;
    private final ItineraryDayRepository dayRepo;
    private final ItineraryItemRepository itemRepo;

    public TripShareService(TripRepository tripRepo,
                            ItineraryDayRepository dayRepo,
                            ItineraryItemRepository itemRepo) {
        this.tripRepo = tripRepo;
        this.dayRepo = dayRepo;
        this.itemRepo = itemRepo;
    }

    @Transactional
    public Trip generateShare(UUID tripId, String userId) {
        Trip trip = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
        trip.enableShare();
        return tripRepo.save(trip);
    }

    @Transactional
    public void revokeShare(UUID tripId, String userId) {
        Trip trip = tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
        trip.revokeShare();
        tripRepo.save(trip);
    }

    @Transactional(readOnly = true)
    public TripWithDays getSharedTrip(UUID shareToken) {
        Trip trip = tripRepo.findByShareTokenAndShareEnabledTrue(shareToken)
                .orElseThrow(TripNotFoundException::new);
        List<ItineraryDay> days = dayRepo.findByTripIdOrderByDayIndex(trip.getId());
        Map<UUID, List<ItineraryItem>> itemsByDay = new HashMap<>();
        for (ItineraryDay day : days) {
            itemsByDay.put(day.getId(), itemRepo.findByItineraryDayIdOrderByPositionAsc(day.getId()));
        }
        return new TripWithDays(trip, days, itemsByDay, null);
    }
}
