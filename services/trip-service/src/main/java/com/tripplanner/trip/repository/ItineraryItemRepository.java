package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    @Query("SELECT COUNT(i) FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    long countByDayIds(@Param("dayIds") List<UUID> dayIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    void deleteByDayIds(@Param("dayIds") List<UUID> dayIds);
}
