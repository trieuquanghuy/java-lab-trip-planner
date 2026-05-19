package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.ItineraryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    @Query("SELECT COUNT(i) FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    long countByDayIds(@Param("dayIds") List<UUID> dayIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    void deleteByDayIds(@Param("dayIds") List<UUID> dayIds);

    List<ItineraryItem> findByItineraryDayIdOrderByPositionAsc(UUID itineraryDayId);

    @Query("SELECT MAX(i.position) FROM ItineraryItem i WHERE i.itineraryDayId = :dayId")
    Optional<Integer> findMaxPositionByDayId(@Param("dayId") UUID dayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ItineraryItem i WHERE i.itineraryDayId = :dayId ORDER BY i.position")
    List<ItineraryItem> findByDayIdForUpdate(@Param("dayId") UUID dayId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ItineraryItem i SET i.position = :pos, i.updatedAt = :now WHERE i.id = :id")
    void updatePosition(@Param("id") UUID id, @Param("pos") int pos, @Param("now") java.time.Instant now);

    @Query("SELECT i FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds AND i.photoUrl IS NOT NULL ORDER BY i.position ASC")
    List<ItineraryItem> findItemsWithPhotoByDayIds(@Param("dayIds") List<UUID> dayIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE trip.itinerary_items SET position = sub.new_pos, updated_at = NOW() " +
            "FROM (SELECT id, (ROW_NUMBER() OVER (ORDER BY position)) * 100 AS new_pos " +
            "FROM trip.itinerary_items WHERE itinerary_day_id = :dayId) sub " +
            "WHERE trip.itinerary_items.id = sub.id", nativeQuery = true)
    void reindexDay(@Param("dayId") UUID dayId);
}
