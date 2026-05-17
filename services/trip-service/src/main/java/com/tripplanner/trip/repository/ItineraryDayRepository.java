package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {
    List<ItineraryDay> findByTripIdOrderByDayIndex(UUID tripId);
    List<ItineraryDay> findByTripIdAndDayDateIn(UUID tripId, List<LocalDate> dates);
    void deleteByTripIdAndIdIn(UUID tripId, List<UUID> ids);

    @Modifying
    @Query("UPDATE ItineraryDay d SET d.dayIndex = :newIndex WHERE d.id = :id")
    void updateDayIndex(@Param("id") UUID id, @Param("newIndex") int newIndex);
}
