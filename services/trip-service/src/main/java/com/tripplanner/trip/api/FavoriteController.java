package com.tripplanner.trip.api;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.trip.api.dto.FavoriteListResponse;
import com.tripplanner.trip.api.dto.FavoriteRequest;
import com.tripplanner.trip.api.dto.FavoriteResponse;
import com.tripplanner.trip.domain.Favorite;
import com.tripplanner.trip.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public ResponseEntity<FavoriteResponse> addFavorite(
            @Valid @RequestBody FavoriteRequest req,
            @AuthenticationPrincipal UserContext ctx) {
        FavoriteService.FavoriteResult result = favoriteService.addFavorite(
                ctx.userId(), req.destinationRef());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(FavoriteResponse.from(result.favorite()));
    }

    @DeleteMapping("/{ref}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable String ref,
            @AuthenticationPrincipal UserContext ctx) {
        favoriteService.removeFavorite(ctx.userId(), ref);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<FavoriteListResponse> listFavorites(
            @AuthenticationPrincipal UserContext ctx) {
        List<Favorite> favorites = favoriteService.listFavorites(ctx.userId());
        List<FavoriteResponse> items = favorites.stream()
                .map(FavoriteResponse::from).toList();
        return ResponseEntity.ok(new FavoriteListResponse(items));
    }
}
