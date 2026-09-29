package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExistingApiProductDetailAdapterTest {

    private static final String DRESS_JSON = "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}";

    private WireMockServer server;
    private CircuitBreakerRegistry circuitBreakers;
    private ExistingApiProductDetailAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        Cache<String, ProductDetail> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .maximumSize(100)
                .build();
        circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        adapter = new ExistingApiProductDetailAdapter(RestClients.withReadTimeout(server.port(), Duration.ofMillis(1000)), cache, circuitBreakers);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void should_return_product_detail_on_200() {
        server.stubFor(get(urlEqualTo("/product/2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DRESS_JSON)));

        Optional<ProductDetail> detail = adapter.findProductDetail("2");

        assertThat(detail).contains(new ProductDetail("2", "Dress", new BigDecimal("19.99"), true));
    }

    @Test
    void should_throw_product_not_found_on_404() {
        server.stubFor(get(urlEqualTo("/product/5"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Product not found\"}")));

        assertThatThrownBy(() -> adapter.findProductDetail("5"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void should_throw_existing_api_exception_on_500() {
        server.stubFor(get(urlEqualTo("/product/6"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.findProductDetail("6"))
                .isInstanceOf(ExistingApiException.class);
    }

    @Test
    void should_fail_with_timeout_when_response_delay_exceeds_read_timeout() {
        server.stubFor(get(urlEqualTo("/product/1000"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withBody(DRESS_JSON)));

        assertThatThrownBy(() -> adapter.findProductDetail("1000"))
                .isInstanceOf(ExistingApiException.class);
    }

    @Test
    void should_serve_repeated_calls_from_cache() {
        server.stubFor(get(urlEqualTo("/product/2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DRESS_JSON)));

        adapter.findProductDetail("2");
        adapter.findProductDetail("2");

        server.verify(1, getRequestedFor(urlEqualTo("/product/2")));
    }

    @Test
    void should_not_cache_not_found() {
        server.stubFor(get(urlEqualTo("/product/5"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Product not found\"}")));

        assertThatThrownBy(() -> adapter.findProductDetail("5")).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> adapter.findProductDetail("5")).isInstanceOf(ProductNotFoundException.class);

        server.verify(2, getRequestedFor(urlEqualTo("/product/5")));
    }

    @Test
    void should_not_cache_failures() {
        server.stubFor(get(urlEqualTo("/product/2"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.findProductDetail("2")).isInstanceOf(ExistingApiException.class);

        server.resetAll();
        server.stubFor(get(urlEqualTo("/product/2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DRESS_JSON)));

        assertThat(adapter.findProductDetail("2"))
                .contains(new ProductDetail("2", "Dress", new BigDecimal("19.99"), true));
    }

    @Test
    void should_serve_a_cached_detail_even_while_the_products_circuit_is_open() {
        server.stubFor(get(urlEqualTo("/product/2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DRESS_JSON)));
        adapter.findProductDetail("2");
        circuitBreakers.circuitBreaker("productDetail-2").transitionToOpenState();

        assertThat(adapter.findProductDetail("2"))
                .contains(new ProductDetail("2", "Dress", new BigDecimal("19.99"), true));
    }

    @Test
    void should_fail_fast_without_calling_the_api_when_the_products_circuit_is_open() {
        circuitBreakers.circuitBreaker("productDetail-6").transitionToOpenState();

        assertThatThrownBy(() -> adapter.findProductDetail("6"))
                .isInstanceOf(ExistingApiException.class);
        server.verify(0, getRequestedFor(urlEqualTo("/product/6")));
    }
}
