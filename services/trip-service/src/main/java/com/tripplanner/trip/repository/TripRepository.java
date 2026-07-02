package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
    Page<Trip> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<Trip> findByShareTokenAndShareEnabledTrue(UUID shareToken);
}
