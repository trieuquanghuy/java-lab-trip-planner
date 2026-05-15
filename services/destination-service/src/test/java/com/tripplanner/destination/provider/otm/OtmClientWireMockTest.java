package com.tripplanner.destination.provider.otm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class OtmClientWireMockTest {

    OtmClient otmClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .build();
        otmClient = new OtmClient(restClient, "test-api-key");
    }

    @Test
    void fetchNearbyParsesOtmResponse() {
        stubFor(get(urlPathEqualTo("/0.1/en/places/radius"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "xid": "N123",
                                    "name": "Eiffel Tower",
                                    "rate": 7,
                                    "kinds": "architecture,historic",
                                    "point": {"lon": 2.2945, "lat": 48.8584}
                                  },
                                  {
                                    "xid": "N456",
                                    "name": "Louvre Museum",
                                    "rate": 7,
                                    "kinds": "museums",
                                    "point": {"lon": 2.3376, "lat": 48.8606}
                                  }
                                ]
                                """)));

        List<OtmPlace> places = otmClient.fetchNearby(48.85, 2.35, 5000, 10);

        assertThat(places).hasSize(2);
        assertThat(places.get(0).xid()).isEqualTo("N123");
        assertThat(places.get(0).name()).isEqualTo("Eiffel Tower");
        assertThat(places.get(0).point().lat()).isEqualTo(48.8584);
        assertThat(places.get(1).xid()).isEqualTo("N456");
    }

    @Test
    void fetchDetailParsesOtmDetailResponse() {
        stubFor(get(urlPathEqualTo("/0.1/en/places/xid/N123"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "xid": "N123",
                                  "name": "Eiffel Tower",
                                  "rate": 7,
                                  "kinds": "architecture,historic",
                                  "point": {"lon": 2.2945, "lat": 48.8584},
                                  "address": {
                                    "road": "5 Avenue Anatole France",
                                    "city": "Paris",
                                    "state": "Île-de-France",
                                    "country": "France",
                                    "postcode": "75007"
                                  },
                                  "wikipedia": "https://en.wikipedia.org/wiki/Eiffel_Tower",
                                  "preview": {
                                    "source": "https://example.com/eiffel.jpg",
                                    "width": 800,
                                    "height": 600
                                  }
                                }
                                """)));

        OtmPlaceDetail detail = otmClient.fetchDetail("N123");

        assertThat(detail).isNotNull();
        assertThat(detail.xid()).isEqualTo("N123");
        assertThat(detail.name()).isEqualTo("Eiffel Tower");
        assertThat(detail.address()).isNotNull();
        assertThat(detail.address().road()).isEqualTo("5 Avenue Anatole France");
        assertThat(detail.wikipedia()).isEqualTo("https://en.wikipedia.org/wiki/Eiffel_Tower");
        assertThat(detail.preview().source()).isEqualTo("https://example.com/eiffel.jpg");
    }

    @Test
    void fetchNearbyReturnsEmptyListForEmptyResponse() {
        stubFor(get(urlPathEqualTo("/0.1/en/places/radius"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        List<OtmPlace> places = otmClient.fetchNearby(48.85, 2.35, 5000, 10);

        assertThat(places).isEmpty();
    }
}
