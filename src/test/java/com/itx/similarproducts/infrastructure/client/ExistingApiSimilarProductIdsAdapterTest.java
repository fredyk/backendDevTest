package com.itx.similarproducts.infrastructure.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExistingApiSimilarProductIdsAdapterTest {

    private WireMockServer server;
    private ExistingApiSimilarProductIdsAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        adapter = new ExistingApiSimilarProductIdsAdapter(RestClients.withReadTimeout(server.port(), Duration.ofMillis(1000)));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void should_return_similar_ids_from_json_numbers() {
        server.stubFor(get(urlEqualTo("/product/1/similarids"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[2,3,4]")));

        List<String> ids = adapter.findSimilarIds("1");

        assertThat(ids).containsExactly("2", "3", "4");
    }

    @Test
    void should_throw_product_not_found_on_404() {
        server.stubFor(get(urlEqualTo("/product/999/similarids"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Not Found")));

        assertThatThrownBy(() -> adapter.findSimilarIds("999"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void should_throw_existing_api_exception_on_500() {
        server.stubFor(get(urlEqualTo("/product/6/similarids"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.findSimilarIds("6"))
                .isInstanceOf(ExistingApiException.class);
    }

    @Test
    void should_fail_with_timeout_when_response_delay_exceeds_read_timeout() {
        server.stubFor(get(urlEqualTo("/product/6/similarids"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withBody("[6]")));

        assertThatThrownBy(() -> adapter.findSimilarIds("6"))
                .isInstanceOf(ExistingApiException.class);
    }

    @Test
    void should_return_empty_list_from_fallback_when_the_circuit_is_open() throws Exception {
        Method fallback = ExistingApiSimilarProductIdsAdapter.class
                .getDeclaredMethod("fallbackSimilarIds", String.class, Throwable.class);

        Object result = fallback.invoke(adapter, "1",
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("existingApi")));

        assertThat(result).isEqualTo(List.of());
    }
}
