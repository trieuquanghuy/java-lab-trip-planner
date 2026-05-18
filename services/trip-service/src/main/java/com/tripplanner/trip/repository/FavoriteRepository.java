package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.Favorite;
import com.tripplanner.trip.domain.FavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndDestinationRef(UUID userId, String destinationRef);

    void deleteByUserIdAndDestinationRef(UUID userId, String destinationRef);
}
