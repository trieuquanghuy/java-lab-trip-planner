package com.tripplanner.destination.destination;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinations")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchResponse> postBatch(@RequestBody BatchRequest request) {
        if (request == null || request.refs() == null || request.refs().isEmpty()) {
            return ResponseEntity.ok(new BatchResponse(List.of()));
        }
        if (request.refs().size() > 50) {
            return ResponseEntity.badRequest().build();
        }
        for (String ref : request.refs()) {
            if (ref == null || !ref.matches("^(otm|fsq):[a-zA-Z0-9_-]+$")) {
                return ResponseEntity.badRequest().build();
            }
        }
        BatchResponse response = batchService.getBatch(request.refs());
        return ResponseEntity.ok(response);
    }
}
