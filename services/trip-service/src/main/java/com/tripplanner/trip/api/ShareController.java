package com.tripplanner.trip.api;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.trip.api.dto.ShareResponse;
import com.tripplanner.trip.api.dto.TripResponse;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.service.TripShareService;
import com.tripplanner.trip.service.TripService.TripWithDays;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ShareController {

    private final TripShareService tripShareService;

    public ShareController(TripShareService tripShareService) {
        this.tripShareService = tripShareService;
    }

    @PostMapping("/api/trips/{id}/share")
    public ResponseEntity<ShareResponse> generateShare(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserContext ctx) {
        Trip trip = tripShareService.generateShare(id, ctx.userId());
        ShareResponse body = new ShareResponse(trip.getShareToken(), "/share/" + trip.getShareToken());
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/api/trips/{id}/share")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserContext ctx) {
        tripShareService.revokeShare(id, ctx.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/share/{token}")
    public ResponseEntity<TripResponse> getSharedTrip(@PathVariable UUID token) {
        TripWithDays result = tripShareService.getSharedTrip(token);
        return ResponseEntity.ok(TripResponse.from(
                result.trip(), result.days(), result.itemsByDay(), result.resolvedCoverImage()));
    }
}
