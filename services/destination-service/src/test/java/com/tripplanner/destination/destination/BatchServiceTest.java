package com.tripplanner.destination.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    DestinationsCacheRepository cacheRepository;

    @Mock
    ProviderMapper mapper;

    BatchService batchService;

    @BeforeEach
    void setUp() {
        batchService = new BatchService(cacheRepository, mapper);
    }

    @Test
    void getBatch_withKnownRefs_returnsMappedItems() {
        DestinationsCacheEntity entity1 = createEntity("otm:N123");
        DestinationsCacheEntity entity2 = createEntity("fsq:abc");
        NearbyItem item1 = new NearbyItem("otm:N123", "Place 1", "museum", BigDecimal.valueOf(4.5), null, BigDecimal.valueOf(48.8), BigDecimal.valueOf(2.3));
        NearbyItem item2 = new NearbyItem("fsq:abc", "Place 2", "restaurant", BigDecimal.valueOf(4.0), null, BigDecimal.valueOf(48.9), BigDecimal.valueOf(2.4));

        when(cacheRepository.findAllById(List.of("otm:N123", "fsq:abc"))).thenReturn(List.of(entity1, entity2));
        when(mapper.toNearbyItem(entity1)).thenReturn(item1);
        when(mapper.toNearbyItem(entity2)).thenReturn(item2);

        BatchResponse result = batchService.getBatch(List.of("otm:N123", "fsq:abc"));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).providerRef()).isEqualTo("otm:N123");
        assertThat(result.items().get(1).providerRef()).isEqualTo("fsq:abc");
    }

    @Test
    void getBatch_withUnknownRefs_returnsOnlyFound() {
        DestinationsCacheEntity entity1 = createEntity("otm:N123");
        NearbyItem item1 = new NearbyItem("otm:N123", "Place 1", "museum", BigDecimal.valueOf(4.5), null, BigDecimal.valueOf(48.8), BigDecimal.valueOf(2.3));

        when(cacheRepository.findAllById(List.of("otm:N123", "otm:UNKNOWN", "fsq:gone")))
                .thenReturn(List.of(entity1));
        when(mapper.toNearbyItem(entity1)).thenReturn(item1);

        BatchResponse result = batchService.getBatch(List.of("otm:N123", "otm:UNKNOWN", "fsq:gone"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).providerRef()).isEqualTo("otm:N123");
    }

    @Test
    void getBatch_withEmptyList_returnsEmptyResponse() {
        when(cacheRepository.findAllById(List.of())).thenReturn(List.of());

        BatchResponse result = batchService.getBatch(List.of());

        assertThat(result.items()).isEmpty();
    }

    private DestinationsCacheEntity createEntity(String providerRef) {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef(providerRef);
        entity.setName("Test Place");
        entity.setCategory("museum");
        entity.setRating(BigDecimal.valueOf(4.5));
        entity.setLat(BigDecimal.valueOf(48.8566));
        entity.setLng(BigDecimal.valueOf(2.3522));
        entity.setPhotos("[]");
        entity.setRaw("{}");
        entity.setFetchedAt(Instant.now());
        return entity;
    }
}
