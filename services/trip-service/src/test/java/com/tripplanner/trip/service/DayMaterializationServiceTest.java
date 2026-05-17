package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.repository.ItineraryItemRepository;
import com.tripplanner.trip.service.exception.ShortenConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayMaterializationServiceTest {

    @Mock private ItineraryDayRepository dayRepo;
    @Mock private ItineraryItemRepository itemRepo;
    @InjectMocks private DayMaterializationService service;

    private Trip makeTrip() {
        return new Trip(UUID.randomUUID(), UUID.randomUUID(), "Test",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14));
    }

    @Test
    void materializeDays_freshTrip_createsAllDays() {
        Trip trip = makeTrip();
        when(dayRepo.findByTripIdOrderByDayIndex(trip.getId())).thenReturn(List.of());

        service.materializeDays(trip, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), false);

        verify(dayRepo).saveAll(anyList());
    }

    @Test
    void materializeDays_shrinkWithItems_throwsConflict() {
        Trip trip = makeTrip();
        UUID day4Id = UUID.randomUUID();
        UUID day5Id = UUID.randomUUID();

        List<ItineraryDay> existing = List.of(
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 10), 1),
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 11), 2),
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 12), 3),
                new ItineraryDay(day4Id, trip.getId(), LocalDate.of(2026, 9, 13), 4),
                new ItineraryDay(day5Id, trip.getId(), LocalDate.of(2026, 9, 14), 5)
        );
        when(dayRepo.findByTripIdOrderByDayIndex(trip.getId())).thenReturn(existing);
        when(itemRepo.countByDayIds(List.of(day4Id, day5Id))).thenReturn(2L);
        when(itemRepo.countByDayIds(List.of(day4Id))).thenReturn(1L);
        when(itemRepo.countByDayIds(List.of(day5Id))).thenReturn(1L);

        assertThatThrownBy(() -> service.materializeDays(trip,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), false))
                .isInstanceOf(ShortenConflictException.class)
                .satisfies(ex -> {
                    ShortenConflictException sce = (ShortenConflictException) ex;
                    assertThat(sce.getOrphanedDays()).hasSize(2);
                });
    }

    @Test
    void materializeDays_shrinkWithConfirm_deletesItemsAndDays() {
        Trip trip = makeTrip();
        UUID day4Id = UUID.randomUUID();
        UUID day5Id = UUID.randomUUID();

        List<ItineraryDay> existing = List.of(
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 10), 1),
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 11), 2),
                new ItineraryDay(UUID.randomUUID(), trip.getId(), LocalDate.of(2026, 9, 12), 3),
                new ItineraryDay(day4Id, trip.getId(), LocalDate.of(2026, 9, 13), 4),
                new ItineraryDay(day5Id, trip.getId(), LocalDate.of(2026, 9, 14), 5)
        );
        when(dayRepo.findByTripIdOrderByDayIndex(trip.getId())).thenReturn(existing);
        when(itemRepo.countByDayIds(List.of(day4Id, day5Id))).thenReturn(2L);

        service.materializeDays(trip, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), true);

        verify(itemRepo).deleteByDayIds(List.of(day4Id, day5Id));
        verify(dayRepo).deleteByTripIdAndIdIn(trip.getId(), List.of(day4Id, day5Id));
    }

    @Test
    void materializeDays_shrinkWithNoDays_doesNotCallDelete() {
        Trip trip = makeTrip();
        when(dayRepo.findByTripIdOrderByDayIndex(trip.getId())).thenReturn(List.of());

        service.materializeDays(trip, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), false);

        verify(itemRepo, never()).deleteByDayIds(any());
        verify(dayRepo, never()).deleteByTripIdAndIdIn(any(), any());
    }
}
