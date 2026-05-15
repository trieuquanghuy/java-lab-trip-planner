package com.tripplanner.destination.destination;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinations")
public class DetailController {

    private final DetailService detailService;

    public DetailController(DetailService detailService) {
        this.detailService = detailService;
    }

    @GetMapping("/{providerRef}")
    public ResponseEntity<DestinationDetailResponse> getDetail(@PathVariable String providerRef) {
        if (providerRef == null || !providerRef.matches("^(otm|fsq):[a-zA-Z0-9_-]+$")) {
            return ResponseEntity.badRequest().build();
        }

        DestinationDetailResponse response = detailService.getDetail(providerRef);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
