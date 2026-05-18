package com.tripplanner.trip.api;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.trip.api.dto.CreateTripRequest;
import com.tripplanner.trip.api.dto.TripListResponse;
import com.tripplanner.trip.api.dto.TripListResponse.TripSummaryResponse;
import com.tripplanner.trip.api.dto.TripResponse;
import com.tripplanner.trip.api.dto.UpdateTripRequest;
import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.service.TripService;
import com.tripplanner.trip.service.TripService.TripWithDays;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest req,
                                               @AuthenticationPrincipal UserContext ctx) {
        TripWithDays result = tripService.create(ctx.userId(), req.name(),
                req.startDate(), req.endDate());
        TripResponse body = TripResponse.from(result.trip(), result.days(),
                result.itemsByDay(), result.resolvedCoverImage());
        return ResponseEntity.created(URI.create("/api/trips/" + body.id())).body(body);
    }

    @GetMapping
    public ResponseEntity<TripListResponse> list(
            @AuthenticationPrincipal UserContext ctx,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Trip> page = tripService.listTrips(ctx.userId(), pageable);
        List<TripSummaryResponse> content = page.getContent().stream()
                .map(t -> new TripSummaryResponse(
                        t.getId(), t.getName(), t.getStartDate(), t.getEndDate(),
                        t.getCoverImageUrl(), t.getCreatedAt(), t.getUpdatedAt()))
                .toList();
        return ResponseEntity.ok(new TripListResponse(
                content, page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> get(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserContext ctx) {
        TripWithDays result = tripService.findTrip(id, ctx.userId());
        return ResponseEntity.ok(TripResponse.from(result.trip(), result.days(),
                result.itemsByDay(), result.resolvedCoverImage()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TripResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateTripRequest req,
                                               @RequestParam(defaultValue = "false") boolean confirmShorten,
                                               @AuthenticationPrincipal UserContext ctx) {
        TripWithDays result = tripService.updateTrip(id, ctx.userId(),
                req.name(), req.startDate(), req.endDate(),
                req.coverImageUrl(), confirmShorten);
        return ResponseEntity.ok(TripResponse.from(result.trip(), result.days(),
                result.itemsByDay(), result.resolvedCoverImage()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserContext ctx) {
        tripService.deleteTrip(id, ctx.userId());
        return ResponseEntity.noContent().build();
    }
}
