package com.tripplanner.destination.city;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<City, Long> {

    @Query(value = """
            SELECT c.* FROM destination.cities c,
                   to_tsquery('simple', unaccent(trim(:query)) || ':*') AS q
            WHERE c.search_tsv @@ q
            ORDER BY ts_rank(c.search_tsv, q) * LOG(c.population + 1) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<City> searchByPrefix(@Param("query") String query, @Param("limit") int limit);
}
