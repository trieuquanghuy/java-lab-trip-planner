package com.tripplanner.trip.api;

import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.trip.support.TripIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripControllerIT extends TripIntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    private static final String USER_A_ID = UUID.randomUUID().toString();
    private static final String USER_A_EMAIL = "usera@test.com";
    private static final String USER_B_ID = UUID.randomUUID().toString();
    private static final String USER_B_EMAIL = "userb@test.com";

    private String tokenA() { return JwtFixtures.mintValid(USER_A_ID, USER_A_EMAIL); }
    private String tokenB() { return JwtFixtures.mintValid(USER_B_ID, USER_B_EMAIL); }

    // --- SC-1: Setting dates creates exactly 5 days; trip appears in list ---

    @Test
    void sc1_createTripWithDatesCreates5DaysAndAppearsInList() throws Exception {
        String body = """
                {"name":"SC-1 Trip","startDate":"2026-09-10","endDate":"2026-09-14"}
                """;
        mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name", is("SC-1 Trip")))
                .andExpect(jsonPath("$.days", hasSize(5)))
                .andExpect(jsonPath("$.days[0].dayDate", is("2026-09-10")))
                .andExpect(jsonPath("$.days[0].dayIndex", is(1)))
                .andExpect(jsonPath("$.days[4].dayDate", is("2026-09-14")))
                .andExpect(jsonPath("$.days[4].dayIndex", is(5)));

        // Verify trip appears in list
        mvc.perform(get("/api/trips")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].name", is("SC-1 Trip")));
    }

    // --- SC-2: Shrink with items → 409; confirmShorten=true → 200 ---

    @Test
    void sc2_shrinkWithoutConfirmReturns409() throws Exception {
        String createBody = """
                {"name":"SC-2 Trip","startDate":"2026-09-10","endDate":"2026-09-14"}
                """;
        MvcResult createResult = mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String tripId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id");

        // Insert an item directly into day 5 via SQL to simulate Phase 6 data
        insertTestItemOnDay(tripId, 5);

        // Attempt to shrink to 3 days without confirmation
        String patchBody = """
                {"endDate":"2026-09-12"}
                """;
        mvc.perform(patch("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("trip.shorten_requires_confirmation")))
                .andExpect(jsonPath("$.orphanedDays").isArray());

        // Retry with confirmShorten=true
        mvc.perform(patch("/api/trips/" + tripId + "?confirmShorten=true")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(3)));
    }

    // --- SC-3: Returning user sees trips; new user sees empty state ---

    @Test
    void sc3_returningUserSeesTripsNewUserSeesEmpty() throws Exception {
        mvc.perform(get("/api/trips")
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void sc3_returningUserSeesExistingTrips() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = JwtFixtures.mintValid(userId, "return@test.com");

        // Create a trip
        mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Returning Trip"}
                                """))
                .andExpect(status().isCreated());

        // Re-fetch — trip should be there
        mvc.perform(get("/api/trips")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].name", is("Returning Trip")));
    }

    // --- SC-4: User A cannot read User B's trip → 404 ---

    @Test
    void sc4_crossUserAccessReturns404() throws Exception {
        String ownerUserId = UUID.randomUUID().toString();
        String ownerToken = JwtFixtures.mintValid(ownerUserId, "owner@test.com");
        String intruderToken = JwtFixtures.mintValid(UUID.randomUUID().toString(), "intruder@test.com");

        // Owner creates trip
        MvcResult result = mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Private Trip"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String tripId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id");

        // Intruder tries to read → 404 (not 403, per D-09)
        mvc.perform(get("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("trip.not_found")));

        // Intruder tries to delete → 404
        mvc.perform(delete("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // Intruder tries to update → 404
        mvc.perform(patch("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- Unauthenticated access → 401 ---

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/api/trips"))
                .andExpect(status().isUnauthorized());
    }

    // --- Additional validation tests ---

    @Test
    void createTripWithBlankNameReturns400() throws Exception {
        mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("validation.failed")));
    }

    @Test
    void createTripWithInvalidDatesReturns400() throws Exception {
        mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad Dates","startDate":"2026-09-14","endDate":"2026-09-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("trip.invalid_date_range")));
    }

    @Test
    void createTripWithoutDatesReturnsEmptyDays() throws Exception {
        mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Dates Trip"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days", empty()));
    }

    @Test
    void deleteTripReturns204() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = JwtFixtures.mintValid(userId, "delete@test.com");

        MvcResult result = mvc.perform(post("/api/trips")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"To Delete"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String tripId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mvc.perform(get("/api/trips/" + tripId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getNonExistentTripReturns404() throws Exception {
        mvc.perform(get("/api/trips/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("trip.not_found")));
    }

    // --- Helper for SC-2: insert an item directly into a day ---

    @Autowired
    private javax.sql.DataSource dataSource;

    private void insertTestItemOnDay(String tripId, int dayIndex) throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, created_at, updated_at)
                     SELECT gen_random_uuid(), d.id, 'test:item1', 100, NOW(), NOW()
                     FROM trip.itinerary_days d
                     WHERE d.trip_id = ?::uuid AND d.day_index = ?
                     """)) {
            stmt.setString(1, tripId);
            stmt.setInt(2, dayIndex);
            stmt.executeUpdate();
        }
    }
}
