package com.tripplanner.trip.api;

import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.trip.support.TripIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FavoriteControllerIT extends TripIntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String USER_EMAIL = "favuser@test.com";

    private String token() { return JwtFixtures.mintValid(USER_ID, USER_EMAIL); }

    @Test
    void addFavorite_returns201() throws Exception {
        String body = """
                {"destinationRef":"tokyo-tower"}
                """;
        mvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationRef", is("tokyo-tower")));
    }

    @Test
    void addFavorite_idempotent_returns200() throws Exception {
        String body = """
                {"destinationRef":"mount-fuji"}
                """;
        // First time → 201
        mvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second time → 200 (idempotent)
        mvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationRef", is("mount-fuji")));
    }

    @Test
    void removeFavorite_returns204() throws Exception {
        // Add first
        String body = """
                {"destinationRef":"shibuya-crossing"}
                """;
        mvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Remove
        mvc.perform(delete("/api/favorites/shibuya-crossing")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void listFavorites_returnsAll() throws Exception {
        // Use a separate user to avoid pollution from other tests
        String userId = UUID.randomUUID().toString();
        String userToken = JwtFixtures.mintValid(userId, "listuser@test.com");

        // Add 3 favorites
        for (String ref : new String[]{"dest-a", "dest-b", "dest-c"}) {
            mvc.perform(post("/api/favorites")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {"destinationRef":"%s"}
                                    """, ref)))
                    .andExpect(status().isCreated());
        }

        // List
        mvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void listFavorites_emptyReturnsEmptyList() throws Exception {
        String userId = UUID.randomUUID().toString();
        String userToken = JwtFixtures.mintValid(userId, "empty@test.com");

        mvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void favorites_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationRef":"unauth-dest"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
