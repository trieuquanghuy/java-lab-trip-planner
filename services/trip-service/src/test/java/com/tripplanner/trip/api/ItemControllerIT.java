package com.tripplanner.trip.api;

import com.jayway.jsonpath.JsonPath;
import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.trip.support.TripIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemControllerIT extends TripIntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    private static final String USER_A_ID = UUID.randomUUID().toString();
    private static final String USER_A_EMAIL = "itema@test.com";
    private static final String USER_B_ID = UUID.randomUUID().toString();
    private static final String USER_B_EMAIL = "itemb@test.com";

    private String tokenA() { return JwtFixtures.mintValid(USER_A_ID, USER_A_EMAIL); }
    private String tokenB() { return JwtFixtures.mintValid(USER_B_ID, USER_B_EMAIL); }

    private String createTripWithDays() throws Exception {
        String body = """
                {"name":"Item Test Trip","startDate":"2026-09-10","endDate":"2026-09-12"}
                """;
        MvcResult result = mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void addItem_appendsWithCorrectPosition() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Add 3 items
        for (int i = 1; i <= 3; i++) {
            String itemBody = String.format("""
                    {"destinationRef":"dest-%d"}
                    """, i);
            mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                            .header("Authorization", "Bearer " + tokenA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position", is(i * 100)));
        }
    }

    @Test
    void addItem_sanitizesNote() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Jsoup Safelist.none() strips <script> content entirely (better security)
        String itemBody = """
                {"destinationRef":"dest-xss","note":"<script>alert(1)</script>"}
                """;
        mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note", is("")));
    }

    @Test
    void patchItem_movesToNewPosition() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Add an item
        String itemBody = """
                {"destinationRef":"dest-move"}
                """;
        MvcResult addResult = mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

        // Move to position 350
        String patchBody = """
                {"position":350}
                """;
        mvc.perform(patch("/api/trips/" + tripId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position", is(350)));
    }

    @Test
    void patchItem_crossDayMove() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String day1Id = JsonPath.read(tripJson, "$.days[0].id");
        String day2Id = JsonPath.read(tripJson, "$.days[1].id");

        // Add item to day 1
        String itemBody = """
                {"destinationRef":"dest-crossday"}
                """;
        MvcResult addResult = mvc.perform(post("/api/trips/" + tripId + "/days/" + day1Id + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

        // Move to day 2
        String patchBody = String.format("""
                {"itineraryDayId":"%s","position":100}
                """, day2Id);
        mvc.perform(patch("/api/trips/" + tripId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraryDayId", is(day2Id)));
    }

    @Test
    void patchItem_dayNotInTrip_returns400() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Add item
        String itemBody = """
                {"destinationRef":"dest-badday"}
                """;
        MvcResult addResult = mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

        // Try to move to a non-existent day
        String fakeDayId = UUID.randomUUID().toString();
        String patchBody = String.format("""
                {"itineraryDayId":"%s"}
                """, fakeDayId);
        mvc.perform(patch("/api/trips/" + tripId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.code", is("trip.day_not_in_trip")));
    }

    @Test
    void deleteItem_returns204() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Add item
        String itemBody = """
                {"destinationRef":"dest-delete"}
                """;
        MvcResult addResult = mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

        // Delete
        mvc.perform(delete("/api/trips/" + tripId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteItem_notOwned_returns404() throws Exception {
        String tripJson = createTripWithDays();
        String tripId = JsonPath.read(tripJson, "$.id");
        String dayId = JsonPath.read(tripJson, "$.days[0].id");

        // Add item as user A
        String itemBody = """
                {"destinationRef":"dest-owned"}
                """;
        MvcResult addResult = mvc.perform(post("/api/trips/" + tripId + "/days/" + dayId + "/items")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

        // Try to delete as user B
        mvc.perform(delete("/api/trips/" + tripId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/trips/" + UUID.randomUUID() + "/days/" + UUID.randomUUID() + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationRef":"dest-unauth"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
