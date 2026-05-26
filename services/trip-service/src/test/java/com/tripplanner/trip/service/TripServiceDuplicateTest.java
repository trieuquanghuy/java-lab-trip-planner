package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class TripServiceDuplicateTest {

    @Mock private TripRepository tripRepo;
    @Mock private ItineraryDayRepository dayRepo;
    @Mock private ItineraryItemRepository itemRepo;
    @Mock private DayMaterializationService dayMaterializationService;
    @InjectMocks private TripService tripService;

    @Captor private ArgumentCaptor<Trip> tripCaptor;
    @Captor private ArgumentCaptor<List<ItineraryDay>> daysCaptor;
    @Captor private ArgumentCaptor<List<ItineraryItem>> itemsCaptor;

    private static final UUID USER_UUID = UUID.randomUUID();
    private static final String USER_ID = USER_UUID.toString();

    @Test
    void duplicateTrip_copiesTripWithPrefixedName() {
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, "My Trip",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of());

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        assertThat(result.trip().getName()).isEqualTo("Copy of My Trip");
        assertThat(result.trip().getUserId()).isEqualTo(USER_UUID);
        assertThat(result.trip().getId()).isNotEqualTo(source.getId());
    }

    @Test
    void duplicateTrip_truncatesNameAt120Chars() {
        String longName = "A".repeat(115); // "Copy of " + 115 = 123 chars > 120
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, longName, null, null);
        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of());

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        assertThat(result.trip().getName()).hasSize(120);
        assertThat(result.trip().getName()).startsWith("Copy of ");
    }

    @Test
    void duplicateTrip_setsNullDates() {
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, "Trip",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of());

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        assertThat(result.trip().getStartDate()).isNull();
        assertThat(result.trip().getEndDate()).isNull();
    }

    @Test
    void duplicateTrip_copiesDaysWithNewIds() {
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, "Trip", null, null);
        ItineraryDay day1 = new ItineraryDay(UUID.randomUUID(), source.getId(),
                LocalDate.of(2026, 6, 1), 0);
        ItineraryDay day2 = new ItineraryDay(UUID.randomUUID(), source.getId(),
                LocalDate.of(2026, 6, 2), 1);
        ItineraryDay day3 = new ItineraryDay(UUID.randomUUID(), source.getId(),
                LocalDate.of(2026, 6, 3), 2);

        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of(day1, day2, day3));
        when(itemRepo.findByItineraryDayIdOrderByPositionAsc(any())).thenReturn(List.of());

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        assertThat(result.days()).hasSize(3);
        assertThat(result.days().get(0).getDayIndex()).isEqualTo(0);
        assertThat(result.days().get(1).getDayIndex()).isEqualTo(1);
        assertThat(result.days().get(2).getDayIndex()).isEqualTo(2);
        // New UUIDs and new tripId
        assertThat(result.days().get(0).getId()).isNotEqualTo(day1.getId());
        assertThat(result.days().get(0).getTripId()).isEqualTo(result.trip().getId());
    }

    @Test
    void duplicateTrip_copiesItemsWithAllFields() {
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, "Trip", null, null);
        ItineraryDay srcDay = new ItineraryDay(UUID.randomUUID(), source.getId(),
                LocalDate.of(2026, 6, 1), 0);
        ItineraryItem item1 = new ItineraryItem(UUID.randomUUID(), srcDay.getId(),
                "otm:abc123", 0, LocalTime.of(9, 0), "Morning visit", "https://photo.url/1.jpg");
        ItineraryItem item2 = new ItineraryItem(UUID.randomUUID(), srcDay.getId(),
                "fsq:def456", 1, LocalTime.of(14, 30), "Afternoon", null);

        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of(srcDay));
        when(itemRepo.findByItineraryDayIdOrderByPositionAsc(srcDay.getId())).thenReturn(List.of(item1, item2));

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        verify(itemRepo).saveAll(itemsCaptor.capture());
        List<ItineraryItem> savedItems = itemsCaptor.getValue();

        assertThat(savedItems).hasSize(2);
        // Item 1 fields preserved
        assertThat(savedItems.get(0).getDestinationRef()).isEqualTo("otm:abc123");
        assertThat(savedItems.get(0).getPosition()).isEqualTo(0);
        assertThat(savedItems.get(0).getTimeSlot()).isEqualTo(LocalTime.of(9, 0));
        assertThat(savedItems.get(0).getNote()).isEqualTo("Morning visit");
        assertThat(savedItems.get(0).getPhotoUrl()).isEqualTo("https://photo.url/1.jpg");
        // New UUID
        assertThat(savedItems.get(0).getId()).isNotEqualTo(item1.getId());
        // Item 2 fields preserved
        assertThat(savedItems.get(1).getDestinationRef()).isEqualTo("fsq:def456");
        assertThat(savedItems.get(1).getPosition()).isEqualTo(1);
        assertThat(savedItems.get(1).getTimeSlot()).isEqualTo(LocalTime.of(14, 30));
        assertThat(savedItems.get(1).getNote()).isEqualTo("Afternoon");
        assertThat(savedItems.get(1).getPhotoUrl()).isNull();
    }

    @Test
    void duplicateTrip_throwsWhenTripNotFound() {
        UUID randomId = UUID.randomUUID();
        when(tripRepo.findByIdAndUserId(randomId, USER_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.duplicateTrip(randomId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void duplicateTrip_copiesCoverImageUrl() {
        Trip source = new Trip(UUID.randomUUID(), USER_UUID, "Trip", null, null);
        source.setCoverImageUrl("https://covers.example.com/my-trip.jpg");
        when(tripRepo.findByIdAndUserId(source.getId(), USER_UUID)).thenReturn(Optional.of(source));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayRepo.findByTripIdOrderByDayIndex(source.getId())).thenReturn(List.of());

        TripService.TripWithDays result = tripService.duplicateTrip(source.getId(), USER_ID);

        assertThat(result.trip().getCoverImageUrl()).isEqualTo("https://covers.example.com/my-trip.jpg");
    }
}
