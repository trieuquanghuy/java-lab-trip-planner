package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.exception.DayNotInTripException;
import com.tripplanner.trip.service.exception.ItemNotFoundException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryItemServiceTest {

    @Mock private TripRepository tripRepo;
    @Mock private ItineraryDayRepository dayRepo;
    @Mock private ItineraryItemRepository itemRepo;
    @InjectMocks private ItineraryItemService itemService;

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final UUID DAY_ID = UUID.randomUUID();
    private static final String USER_ID = UUID.randomUUID().toString();

    private void stubOwnership() {
        Trip trip = new Trip(TRIP_ID, UUID.fromString(USER_ID), "Trip", null, null);
        when(tripRepo.findByIdAndUserId(TRIP_ID, UUID.fromString(USER_ID)))
                .thenReturn(Optional.of(trip));
    }

    private void stubDay() {
        ItineraryDay day = new ItineraryDay(DAY_ID, TRIP_ID, null, 1);
        when(dayRepo.findById(DAY_ID)).thenReturn(Optional.of(day));
    }

    @Test
    void addItem_firstItem_getsPosition100() {
        stubOwnership();
        stubDay();
        when(itemRepo.findByDayIdForUpdate(DAY_ID)).thenReturn(List.of());
        when(itemRepo.findMaxPositionByDayId(DAY_ID)).thenReturn(Optional.empty());
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ItineraryItem result = itemService.addItem(TRIP_ID, DAY_ID, USER_ID,
                "dest-123", null, null, null);

        assertThat(result.getPosition()).isEqualTo(100);
        assertThat(result.getDestinationRef()).isEqualTo("dest-123");
    }

    @Test
    void addItem_appendsAtMaxPlus100() {
        stubOwnership();
        stubDay();
        when(itemRepo.findByDayIdForUpdate(DAY_ID)).thenReturn(List.of());
        when(itemRepo.findMaxPositionByDayId(DAY_ID)).thenReturn(Optional.of(300));
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ItineraryItem result = itemService.addItem(TRIP_ID, DAY_ID, USER_ID,
                "dest-456", null, null, null);

        assertThat(result.getPosition()).isEqualTo(400);
    }

    @Test
    void addItem_sanitizesHtmlFromNote() {
        stubOwnership();
        stubDay();
        when(itemRepo.findByDayIdForUpdate(DAY_ID)).thenReturn(List.of());
        when(itemRepo.findMaxPositionByDayId(DAY_ID)).thenReturn(Optional.empty());
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        // Jsoup Safelist.none() strips <script> content entirely (not just the tag)
        ItineraryItem result = itemService.addItem(TRIP_ID, DAY_ID, USER_ID,
                "dest-xss", null, "<script>alert(1)</script>", null);

        assertThat(result.getNote()).isEmpty();
    }

    @Test
    void addItem_boldTagStripped() {
        stubOwnership();
        stubDay();
        when(itemRepo.findByDayIdForUpdate(DAY_ID)).thenReturn(List.of());
        when(itemRepo.findMaxPositionByDayId(DAY_ID)).thenReturn(Optional.empty());
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ItineraryItem result = itemService.addItem(TRIP_ID, DAY_ID, USER_ID,
                "dest-bold", null, "<b>bold</b>", null);

        assertThat(result.getNote()).isEqualTo("bold");
    }

    @Test
    void addItem_nullNote_remainsNull() {
        stubOwnership();
        stubDay();
        when(itemRepo.findByDayIdForUpdate(DAY_ID)).thenReturn(List.of());
        when(itemRepo.findMaxPositionByDayId(DAY_ID)).thenReturn(Optional.empty());
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ItineraryItem result = itemService.addItem(TRIP_ID, DAY_ID, USER_ID,
                "dest-null", null, null, null);

        assertThat(result.getNote()).isNull();
    }

    @Test
    void addItem_tripNotOwned_throws() {
        when(tripRepo.findByIdAndUserId(TRIP_ID, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                itemService.addItem(TRIP_ID, DAY_ID, USER_ID, "dest", null, null, null))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void addItem_dayNotInTrip_throws() {
        stubOwnership();
        // Day belongs to different trip
        UUID otherTripId = UUID.randomUUID();
        ItineraryDay day = new ItineraryDay(DAY_ID, otherTripId, null, 1);
        when(dayRepo.findById(DAY_ID)).thenReturn(Optional.of(day));

        assertThatThrownBy(() ->
                itemService.addItem(TRIP_ID, DAY_ID, USER_ID, "dest", null, null, null))
                .isInstanceOf(DayNotInTripException.class);
    }

    @Test
    void updateItem_crossDayMove_updatesDay() {
        stubOwnership();
        UUID newDayId = UUID.randomUUID();
        ItineraryDay day1 = new ItineraryDay(DAY_ID, TRIP_ID, null, 1);
        ItineraryDay day2 = new ItineraryDay(newDayId, TRIP_ID, null, 2);
        when(dayRepo.findByTripIdOrderByDayIndex(TRIP_ID)).thenReturn(List.of(day1, day2));

        UUID itemId = UUID.randomUUID();
        ItineraryItem item = new ItineraryItem(itemId, DAY_ID, "dest", 100, null, null, null);
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepo.findByDayIdForUpdate(newDayId)).thenReturn(List.of());
        when(itemRepo.findByItineraryDayIdOrderByPositionAsc(newDayId)).thenReturn(List.of());
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ItineraryItem result = itemService.updateItem(TRIP_ID, itemId, USER_ID,
                200, newDayId, null, null, null);

        assertThat(result.getItineraryDayId()).isEqualTo(newDayId);
        assertThat(result.getPosition()).isEqualTo(200);
    }

    @Test
    void updateItem_invalidTargetDay_throws() {
        stubOwnership();
        UUID invalidDayId = UUID.randomUUID();
        ItineraryDay day1 = new ItineraryDay(DAY_ID, TRIP_ID, null, 1);
        when(dayRepo.findByTripIdOrderByDayIndex(TRIP_ID)).thenReturn(List.of(day1));

        UUID itemId = UUID.randomUUID();
        ItineraryItem item = new ItineraryItem(itemId, DAY_ID, "dest", 100, null, null, null);
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                itemService.updateItem(TRIP_ID, itemId, USER_ID, null, invalidDayId, null, null, null))
                .isInstanceOf(DayNotInTripException.class);
    }

    @Test
    void updateItem_itemNotFound_throws() {
        stubOwnership();
        ItineraryDay day1 = new ItineraryDay(DAY_ID, TRIP_ID, null, 1);
        when(dayRepo.findByTripIdOrderByDayIndex(TRIP_ID)).thenReturn(List.of(day1));

        UUID itemId = UUID.randomUUID();
        when(itemRepo.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                itemService.updateItem(TRIP_ID, itemId, USER_ID, null, null, null, null, null))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void deleteItem_removesItem() {
        stubOwnership();
        ItineraryDay day1 = new ItineraryDay(DAY_ID, TRIP_ID, null, 1);
        when(dayRepo.findByTripIdOrderByDayIndex(TRIP_ID)).thenReturn(List.of(day1));

        UUID itemId = UUID.randomUUID();
        ItineraryItem item = new ItineraryItem(itemId, DAY_ID, "dest", 100, null, null, null);
        when(itemRepo.findById(itemId)).thenReturn(Optional.of(item));

        itemService.deleteItem(TRIP_ID, itemId, USER_ID);

        verify(itemRepo).delete(item);
    }

    @Test
    void deleteItem_notOwned_throws() {
        when(tripRepo.findByIdAndUserId(TRIP_ID, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() ->
                itemService.deleteItem(TRIP_ID, itemId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }
}
