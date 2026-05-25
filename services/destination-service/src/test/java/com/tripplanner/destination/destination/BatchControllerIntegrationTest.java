package com.tripplanner.destination.destination;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BatchController.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
class BatchControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BatchService batchService;

    @Test
    void postBatch_validRefs_returns200WithItems() throws Exception {
        NearbyItem item1 = new NearbyItem("otm:N123", "Eiffel Tower", "architecture",
                BigDecimal.valueOf(7), null, BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35));
        NearbyItem item2 = new NearbyItem("fsq:abc", "Louvre", "museum",
                BigDecimal.valueOf(9), null, BigDecimal.valueOf(48.86), BigDecimal.valueOf(2.34));
        when(batchService.getBatch(List.of("otm:N123", "fsq:abc")))
                .thenReturn(new BatchResponse(List.of(item1, item2)));

        mockMvc.perform(post("/api/destinations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refs\":[\"otm:N123\",\"fsq:abc\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Eiffel Tower"))
                .andExpect(jsonPath("$.items[1].name").value("Louvre"));
    }

    @Test
    void postBatch_emptyRefs_returns200EmptyArray() throws Exception {
        mockMvc.perform(post("/api/destinations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refs\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void postBatch_tooManyRefs_returns400() throws Exception {
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            refs.add("otm:ref" + i);
        }
        String body = "{\"refs\":" + refs.toString().replace("otm:", "\"otm:").replace(",", "\",").replace("]", "\"]").replace("[\"", "[\"") + "}";
        // Build a proper JSON array
        StringBuilder sb = new StringBuilder("{\"refs\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"otm:ref").append(i).append("\"");
        }
        sb.append("]}");

        mockMvc.perform(post("/api/destinations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postBatch_invalidRefFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/destinations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refs\":[\"invalid-no-prefix\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postBatch_sqlInjectionAttempt_returns400() throws Exception {
        mockMvc.perform(post("/api/destinations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refs\":[\"otm:'; DROP TABLE--\"]}"))
                .andExpect(status().isBadRequest());
    }
}
