package com.tripplanner.trip.api;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.trip.api.dto.CreateItemRequest;
import com.tripplanner.trip.api.dto.ItemResponse;
import com.tripplanner.trip.api.dto.UpdateItemRequest;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.service.ItineraryItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class ItemController {

    private final ItineraryItemService itemService;

    public ItemController(ItineraryItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/days/{dayId}/items")
    public ResponseEntity<ItemResponse> addItem(
            @PathVariable UUID tripId, @PathVariable UUID dayId,
            @Valid @RequestBody CreateItemRequest req,
            @AuthenticationPrincipal UserContext ctx) {
        ItineraryItem item = itemService.addItem(tripId, dayId, ctx.userId(),
                req.destinationRef(), req.timeSlot(), req.note(), req.photoUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemResponse.from(item));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<ItemResponse> updateItem(
            @PathVariable UUID tripId, @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest req,
            @AuthenticationPrincipal UserContext ctx) {
        ItineraryItem item = itemService.updateItem(tripId, itemId, ctx.userId(),
                req.position(), req.itineraryDayId(), req.timeSlot(), req.note(), req.photoUrl());
        return ResponseEntity.ok(ItemResponse.from(item));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable UUID tripId, @PathVariable UUID itemId,
            @AuthenticationPrincipal UserContext ctx) {
        itemService.deleteItem(tripId, itemId, ctx.userId());
        return ResponseEntity.noContent().build();
    }
}
