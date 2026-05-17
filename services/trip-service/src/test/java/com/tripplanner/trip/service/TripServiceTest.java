package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.exception.InvalidDateRangeException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepo;
    @Mock private ItineraryDayRepository dayRepo;
    @Mock private DayMaterializationService dayMaterializationService;
    @InjectMocks private TripService tripService;

    private static final String USER_ID = UUID.randomUUID().toString();

    @Test
    void create_withDates_savesAndMaterializesDays() {
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dayMaterializationService.materializeDays(any(), any(), any(), eq(false)))
                .thenReturn(List.of());

        TripService.TripWithDays result = tripService.create(USER_ID, "Test Trip",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14));

        assertThat(result.trip().getName()).isEqualTo("Test Trip");
        verify(tripRepo).save(any(Trip.class));
        verify(dayMaterializationService).materializeDays(any(), eq(LocalDate.of(2026, 9, 10)),
                eq(LocalDate.of(2026, 9, 14)), eq(false));
    }

    @Test
    void create_withoutDates_doesNotMaterialize() {
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripService.TripWithDays result = tripService.create(USER_ID, "No Dates", null, null);

        assertThat(result.days()).isEmpty();
        verify(dayMaterializationService, never()).materializeDays(any(), any(), any(), anyBoolean());
    }

    @Test
    void create_withInvalidDates_throwsInvalidDateRange() {
        assertThatThrownBy(() -> tripService.create(USER_ID, "Bad",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 10)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void findTrip_existingOwner_returnsTripWithDays() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip(tripId, UUID.fromString(USER_ID), "My Trip", null, null);
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.of(trip));
        when(dayRepo.findByTripIdOrderByDayIndex(tripId)).thenReturn(List.of());

        TripService.TripWithDays result = tripService.findTrip(tripId, USER_ID);

        assertThat(result.trip().getId()).isEqualTo(tripId);
    }

    @Test
    void findTrip_nonOwner_throwsNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.findTrip(tripId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void listTrips_returnsPageForUser() {
        Trip trip = new Trip(UUID.randomUUID(), UUID.fromString(USER_ID), "Trip", null, null);
        Page<Trip> page = new PageImpl<>(List.of(trip));
        when(tripRepo.findByUserIdOrderByCreatedAtDesc(eq(UUID.fromString(USER_ID)), any()))
                .thenReturn(page);

        Page<Trip> result = tripService.listTrips(USER_ID, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void deleteTrip_existingOwner_deletes() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip(tripId, UUID.fromString(USER_ID), "To Delete", null, null);
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.of(trip));

        tripService.deleteTrip(tripId, USER_ID);

        verify(tripRepo).delete(trip);
    }

    @Test
    void deleteTrip_nonOwner_throwsNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.deleteTrip(tripId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }
}
