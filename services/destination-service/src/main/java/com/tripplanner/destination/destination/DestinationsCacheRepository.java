package com.tripplanner.destination.destination;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DestinationsCacheRepository extends JpaRepository<DestinationsCacheEntity, String> {

    @Query(value = """
            SELECT d.* FROM destination.destinations_cache d
            WHERE earth_box(ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)), :radius)
                  @> ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
              AND earth_distance(
                    ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)),
                    ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
                  ) <= :radius
            ORDER BY earth_distance(
                       ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)),
                       ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
                     ) ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<DestinationsCacheEntity> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") double radius,
            @Param("lim") int limit);

    @Query(value = """
            SELECT d.* FROM destination.destinations_cache d
            WHERE d.fetched_at > NOW() - INTERVAL '24 hours'
              AND earth_box(ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)), :radius)
                  @> ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
              AND earth_distance(
                    ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)),
                    ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
                  ) <= :radius
            ORDER BY earth_distance(
                       ll_to_earth(CAST(:lat AS float8), CAST(:lng AS float8)),
                       ll_to_earth(CAST(d.lat AS float8), CAST(d.lng AS float8))
                     ) ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<DestinationsCacheEntity> findNearbyFresh(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radius") double radius,
            @Param("lim") int limit);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO destination.destinations_cache
                (provider_ref, name, category, rating, lat, lng, address,
                 photos, opening_hours, website, raw, fetched_at)
            VALUES (:providerRef, :name, :category, :rating, :lat, :lng, :address,
                    CAST(:photos AS jsonb), CAST(:openingHours AS jsonb),
                    :website, CAST(:raw AS jsonb), :fetchedAt)
            ON CONFLICT (provider_ref) DO UPDATE SET
                name = EXCLUDED.name,
                category = EXCLUDED.category,
                rating = EXCLUDED.rating,
                lat = EXCLUDED.lat,
                lng = EXCLUDED.lng,
                address = EXCLUDED.address,
                photos = EXCLUDED.photos,
                opening_hours = EXCLUDED.opening_hours,
                website = EXCLUDED.website,
                raw = EXCLUDED.raw,
                fetched_at = EXCLUDED.fetched_at
            WHERE destination.destinations_cache.fetched_at < EXCLUDED.fetched_at
            """, nativeQuery = true)
    void upsert(
            @Param("providerRef") String providerRef,
            @Param("name") String name,
            @Param("category") String category,
            @Param("rating") BigDecimal rating,
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng,
            @Param("address") String address,
            @Param("photos") String photos,
            @Param("openingHours") String openingHours,
            @Param("website") String website,
            @Param("raw") String raw,
            @Param("fetchedAt") Instant fetchedAt);
}
