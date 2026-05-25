package com.tripplanner.destination.destination;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchService {

    private final DestinationsCacheRepository cacheRepository;
    private final ProviderMapper mapper;

    public BatchService(DestinationsCacheRepository cacheRepository, ProviderMapper mapper) {
        this.cacheRepository = cacheRepository;
        this.mapper = mapper;
    }

    public BatchResponse getBatch(List<String> refs) {
        List<DestinationsCacheEntity> entities = cacheRepository.findAllById(refs);
        List<NearbyItem> items = entities.stream()
                .map(mapper::toNearbyItem)
                .toList();
        return new BatchResponse(items);
    }
}
