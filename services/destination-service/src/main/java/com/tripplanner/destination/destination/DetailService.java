package com.tripplanner.destination.destination;

import com.tripplanner.destination.provider.otm.OtmClient;
import com.tripplanner.destination.provider.otm.OtmPlaceDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DetailService {

    private static final Logger log = LoggerFactory.getLogger(DetailService.class);
    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);

    private final DestinationsCacheRepository cacheRepository;
    private final OtmClient otmClient;
    private final ProviderMapper mapper;

    public DetailService(DestinationsCacheRepository cacheRepository,
                         OtmClient otmClient,
                         ProviderMapper mapper) {
        this.cacheRepository = cacheRepository;
        this.otmClient = otmClient;
        this.mapper = mapper;
    }

    public DestinationDetailResponse getDetail(String providerRef) {
        // Check cache first
        Optional<DestinationsCacheEntity> cached = cacheRepository.findById(providerRef);
        if (cached.isPresent()) {
            DestinationsCacheEntity entity = cached.get();
            boolean isStale = entity.getFetchedAt().isBefore(Instant.now().minus(STALE_THRESHOLD));
            if (!isStale) {
                log.debug("Cache hit (fresh) for providerRef={}", providerRef);
                return mapper.toDetailResponse(entity, true);
            }
            // Stale — try to refresh from provider
            DestinationsCacheEntity refreshed = refreshFromProvider(providerRef);
            if (refreshed != null) {
                log.debug("Refreshed stale entry for providerRef={}", providerRef);
                return mapper.toDetailResponse(refreshed, false);
            }
            // Provider down — serve stale
            log.debug("Serving stale entry for providerRef={} (provider unavailable)", providerRef);
            return mapper.toDetailResponse(entity, true);
        }
        // Not in cache — fetch from provider
        DestinationsCacheEntity fetched = refreshFromProvider(providerRef);
        if (fetched != null) {
            return mapper.toDetailResponse(fetched, false);
        }
        return null; // Controller returns 404
    }

    private DestinationsCacheEntity refreshFromProvider(String providerRef) {
        if (providerRef.startsWith("otm:")) {
            String xid = providerRef.substring(4);
            OtmPlaceDetail detail = otmClient.fetchDetail(xid);
            if (detail != null) {
                DestinationsCacheEntity entity = mapper.fromOtmDetail(detail);
                cacheRepository.upsert(
                        entity.getProviderRef(), entity.getName(), entity.getCategory(),
                        entity.getRating(), entity.getLat(), entity.getLng(),
                        entity.getAddress(), entity.getPhotos(), entity.getOpeningHours(),
                        entity.getWebsite(), entity.getRaw(), entity.getFetchedAt());
                return entity;
            }
        }
        // FSQ detail not implemented in v1 (enrichment only)
        return null;
    }
}
