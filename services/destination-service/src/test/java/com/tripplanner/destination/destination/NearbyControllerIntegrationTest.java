package com.tripplanner.destination.destination;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {NearbyController.class, DetailController.class},
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
class NearbyControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean NearbyService nearbyService;
    @MockitoBean DetailService detailService;

    @Test
    void getNearbyReturns200WithValidParams() throws Exception {
        NearbyItem item = new NearbyItem("otm:N123", "Eiffel Tower", "architecture",
                BigDecimal.valueOf(7), null, BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35));
        NearbyResponse response = new NearbyResponse(List.of(item), false, ProviderStatus.allOk());
        when(nearbyService.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/api/destinations")
                        .param("lat", "48.85")
                        .param("lng", "2.35")
                        .param("radius", "5000")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Eiffel Tower"))
                .andExpect(jsonPath("$.providerStatus.openTripMap").value("ok"));
    }

    @Test
    void getNearbyReturns400ForInvalidLat() throws Exception {
        mockMvc.perform(get("/api/destinations")
                        .param("lat", "91")
                        .param("lng", "2.35"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNearbyReturns400ForInvalidLng() throws Exception {
        mockMvc.perform(get("/api/destinations")
                        .param("lat", "48.85")
                        .param("lng", "181"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNearbyReturns400ForInvalidRadius() throws Exception {
        mockMvc.perform(get("/api/destinations")
                        .param("lat", "48.85")
                        .param("lng", "2.35")
                        .param("radius", "60000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDetailReturns200ForValidProviderRef() throws Exception {
        DestinationDetailResponse response = new DestinationDetailResponse(
                "otm:N123", "Eiffel Tower", "architecture", null,
                BigDecimal.valueOf(7), BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35),
                "Paris", "https://example.com", List.of(), null, false, java.time.Instant.now());
        when(detailService.getDetail("otm:N123")).thenReturn(response);

        mockMvc.perform(get("/api/destinations/otm:N123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Eiffel Tower"));
    }

    @Test
    void getDetailReturns404WhenNotFound() throws Exception {
        when(detailService.getDetail("otm:N999")).thenReturn(null);

        mockMvc.perform(get("/api/destinations/otm:N999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDetailReturns400ForInvalidProviderRef() throws Exception {
        mockMvc.perform(get("/api/destinations/invalid-ref"))
                .andExpect(status().isBadRequest());
    }
}
