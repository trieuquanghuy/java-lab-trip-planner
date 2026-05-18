package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.Favorite;
import com.tripplanner.trip.repository.FavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepo;
    @InjectMocks private FavoriteService favoriteService;

    private static final String USER_ID = UUID.randomUUID().toString();

    @Test
    void addFavorite_new_createsAndReturnsCreatedTrue() {
        when(favoriteRepo.findByUserIdAndDestinationRef(UUID.fromString(USER_ID), "dest-1"))
                .thenReturn(Optional.empty());
        when(favoriteRepo.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

        FavoriteService.FavoriteResult result = favoriteService.addFavorite(USER_ID, "dest-1");

        assertThat(result.created()).isTrue();
        assertThat(result.favorite().getDestinationRef()).isEqualTo("dest-1");
        verify(favoriteRepo).save(any(Favorite.class));
    }

    @Test
    void addFavorite_existing_returnsCreatedFalse() {
        Favorite existing = new Favorite(UUID.fromString(USER_ID), "dest-1");
        when(favoriteRepo.findByUserIdAndDestinationRef(UUID.fromString(USER_ID), "dest-1"))
                .thenReturn(Optional.of(existing));

        FavoriteService.FavoriteResult result = favoriteService.addFavorite(USER_ID, "dest-1");

        assertThat(result.created()).isFalse();
        assertThat(result.favorite()).isEqualTo(existing);
        verify(favoriteRepo, never()).save(any());
    }

    @Test
    void removeFavorite_callsDelete() {
        favoriteService.removeFavorite(USER_ID, "dest-1");

        verify(favoriteRepo).deleteByUserIdAndDestinationRef(UUID.fromString(USER_ID), "dest-1");
    }

    @Test
    void listFavorites_returnsOrdered() {
        Favorite fav1 = new Favorite(UUID.fromString(USER_ID), "dest-1");
        Favorite fav2 = new Favorite(UUID.fromString(USER_ID), "dest-2");
        when(favoriteRepo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(USER_ID)))
                .thenReturn(List.of(fav2, fav1));

        List<Favorite> result = favoriteService.listFavorites(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDestinationRef()).isEqualTo("dest-2");
    }
}
