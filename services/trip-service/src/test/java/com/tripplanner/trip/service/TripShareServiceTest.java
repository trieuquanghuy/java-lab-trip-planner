package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.service.TripService.TripWithDays;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripShareServiceTest {

    @Mock private TripRepository tripRepo;
    @Mock private ItineraryDayRepository dayRepo;
    @Mock private ItineraryItemRepository itemRepo;
    @InjectMocks private TripShareService tripShareService;

    private static final String USER_ID = UUID.randomUUID().toString();

    private Trip makeTrip() {
        return new Trip(UUID.randomUUID(), UUID.fromString(USER_ID), "Test Trip", null, null);
    }

    @Test
    void generateShare_setsTokenAndEnabled() {
        Trip trip = makeTrip();
        when(tripRepo.findByIdAndUserId(trip.getId(), UUID.fromString(USER_ID)))
                .thenReturn(Optional.of(trip));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripShareService.generateShare(trip.getId(), USER_ID);

        assertThat(result.getShareToken()).isNotNull();
        assertThat(result.isShareEnabled()).isTrue();
        verify(tripRepo).save(trip);
    }

    @Test
    void generateShare_throwsWhenTripNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripShareService.generateShare(tripId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void revokeShare_clearsTokenAndDisables() {
        Trip trip = makeTrip();
        trip.enableShare();
        when(tripRepo.findByIdAndUserId(trip.getId(), UUID.fromString(USER_ID)))
                .thenReturn(Optional.of(trip));
        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        tripShareService.revokeShare(trip.getId(), USER_ID);

        assertThat(trip.getShareToken()).isNull();
        assertThat(trip.isShareEnabled()).isFalse();
        verify(tripRepo).save(trip);
    }

    @Test
    void revokeShare_throwsWhenTripNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(USER_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripShareService.revokeShare(tripId, USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void getSharedTrip_returnsData() {
        Trip trip = makeTrip();
        trip.enableShare();
        when(tripRepo.findByShareTokenAndShareEnabledTrue(trip.getShareToken()))
                .thenReturn(Optional.of(trip));
        when(dayRepo.findByTripIdOrderByDayIndex(trip.getId())).thenReturn(List.of());

        TripWithDays result = tripShareService.getSharedTrip(trip.getShareToken());

        assertThat(result.trip().getId()).isEqualTo(trip.getId());
        assertThat(result.days()).isEmpty();
    }

    @Test
    void getSharedTrip_throwsWhenTokenNotFound() {
        UUID token = UUID.randomUUID();
        when(tripRepo.findByShareTokenAndShareEnabledTrue(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripShareService.getSharedTrip(token))
                .isInstanceOf(TripNotFoundException.class);
    }
}
