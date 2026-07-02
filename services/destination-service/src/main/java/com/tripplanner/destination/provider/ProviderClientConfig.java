package com.tripplanner.destination.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ProviderClientConfig {

    @Bean
    public RestClient otmRestClient(@Value("${otm.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient fsqRestClient(
            @Value("${fsq.base-url}") String baseUrl,
            @Value("${fsq.api-key}") String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public RestClient osrmRestClient(
            @Value("${osrm.base-url:http://router.project-osrm.org}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
