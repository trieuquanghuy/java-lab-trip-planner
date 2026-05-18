package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.exception.DayNotInTripException;
import com.tripplanner.trip.service.exception.ItemNotFoundException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class ItineraryItemService {

    private final TripRepository tripRepo;
    private final ItineraryDayRepository dayRepo;
    private final ItineraryItemRepository itemRepo;

    public ItineraryItemService(TripRepository tripRepo,
                                ItineraryDayRepository dayRepo,
                                ItineraryItemRepository itemRepo) {
        this.tripRepo = tripRepo;
        this.dayRepo = dayRepo;
        this.itemRepo = itemRepo;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ItineraryItem addItem(UUID tripId, UUID dayId, String userId,
                                 String destinationRef, LocalTime timeSlot,
                                 String note, String photoUrl) {
        verifyOwnership(tripId, userId);
        verifyDayBelongsToTrip(dayId, tripId);

        // D-03: SELECT FOR UPDATE on day's items to serialize concurrent position writes
        itemRepo.findByDayIdForUpdate(dayId);

        int position = itemRepo.findMaxPositionByDayId(dayId).orElse(0) + 100;
        String sanitizedNote = sanitize(note);

        ItineraryItem item = new ItineraryItem(
                UUID.randomUUID(), dayId, destinationRef, position, timeSlot, sanitizedNote, photoUrl);
        return itemRepo.save(item);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ItineraryItem updateItem(UUID tripId, UUID itemId, String userId,
                                    Integer newPosition, UUID newDayId,
                                    LocalTime timeSlot, String note, String photoUrl) {
        verifyOwnership(tripId, userId);

        List<ItineraryDay> days = dayRepo.findByTripIdOrderByDayIndex(tripId);
        List<UUID> dayIds = days.stream().map(ItineraryDay::getId).toList();

        ItineraryItem item = itemRepo.findById(itemId)
                .filter(i -> dayIds.contains(i.getItineraryDayId()))
                .orElseThrow(ItemNotFoundException::new);

        UUID effectiveDayId = item.getItineraryDayId();

        // Cross-day move
        if (newDayId != null && !newDayId.equals(item.getItineraryDayId())) {
            if (!dayIds.contains(newDayId)) {
                throw new DayNotInTripException();
            }
            item.setItineraryDayId(newDayId);
            effectiveDayId = newDayId;
        }

        // D-03: acquire lock on target day before position writes
        itemRepo.findByDayIdForUpdate(effectiveDayId);

        if (newPosition != null) {
            // D-02: proactive reindex when gap < 2
            List<ItineraryItem> itemsInDay = itemRepo.findByItineraryDayIdOrderByPositionAsc(effectiveDayId);
            if (needsReindex(itemsInDay, newPosition)) {
                itemRepo.reindexDay(effectiveDayId);
            }
            item.setPosition(newPosition);
        }

        if (note != null) {
            item.setNote(sanitize(note));
        }
        if (timeSlot != null) {
            item.setTimeSlot(timeSlot);
        }
        if (photoUrl != null) {
            item.setPhotoUrl(photoUrl);
        }

        item.setUpdatedAt(Instant.now());
        return itemRepo.save(item);
    }

    @Transactional
    public void deleteItem(UUID tripId, UUID itemId, String userId) {
        verifyOwnership(tripId, userId);

        List<ItineraryDay> days = dayRepo.findByTripIdOrderByDayIndex(tripId);
        List<UUID> dayIds = days.stream().map(ItineraryDay::getId).toList();

        ItineraryItem item = itemRepo.findById(itemId)
                .filter(i -> dayIds.contains(i.getItineraryDayId()))
                .orElseThrow(ItemNotFoundException::new);

        itemRepo.delete(item);
    }

    private void verifyOwnership(UUID tripId, String userId) {
        tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
    }

    private void verifyDayBelongsToTrip(UUID dayId, UUID tripId) {
        ItineraryDay day = dayRepo.findById(dayId)
                .orElseThrow(DayNotInTripException::new);
        if (!day.getTripId().equals(tripId)) {
            throw new DayNotInTripException();
        }
    }

    private boolean needsReindex(List<ItineraryItem> items, int targetPosition) {
        for (int i = 0; i < items.size() - 1; i++) {
            int current = items.get(i).getPosition();
            int next = items.get(i + 1).getPosition();
            if (targetPosition >= current && targetPosition <= next && (next - current) < 2) {
                return true;
            }
        }
        return false;
    }

    private String sanitize(String note) {
        if (note == null) return null;
        return Jsoup.clean(note, Safelist.none());
    }
}
