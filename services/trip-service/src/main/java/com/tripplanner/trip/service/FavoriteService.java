package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.Favorite;
import com.tripplanner.trip.repository.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;

    public FavoriteService(FavoriteRepository favoriteRepo) {
        this.favoriteRepo = favoriteRepo;
    }

    @Transactional(readOnly = true)
    public List<Favorite> listFavorites(String userId) {
        return favoriteRepo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
    }

    /**
     * Idempotent favorite — per D-08.
     * Returns the Favorite and whether it was newly created (201 vs 200).
     */
    @Transactional
    public FavoriteResult addFavorite(String userId, String destinationRef) {
        UUID uid = UUID.fromString(userId);
        Optional<Favorite> existing = favoriteRepo.findByUserIdAndDestinationRef(uid, destinationRef);
        if (existing.isPresent()) {
            return new FavoriteResult(existing.get(), false);
        }
        Favorite fav = new Favorite(uid, destinationRef);
        favoriteRepo.save(fav);
        return new FavoriteResult(fav, true);
    }

    @Transactional
    public void removeFavorite(String userId, String destinationRef) {
        favoriteRepo.deleteByUserIdAndDestinationRef(UUID.fromString(userId), destinationRef);
    }

    public record FavoriteResult(Favorite favorite, boolean created) {}
}
