package com.itx.similarproducts.infrastructure.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.itx.similarproducts.domain.model.ProductDetail;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsControllerIT {

    // Started statically so its port is known before the Spring context reads existing.api.base-url.
    private static final WireMockServer EXISTING_API = new WireMockServer(options().dynamicPort().containerThreads(100));

    static {
        EXISTING_API.start();
    }

    @DynamicPropertySource
    static void existingApiProperties(DynamicPropertyRegistry registry) {
        registry.add("existing.api.base-url", () -> "http://localhost:" + EXISTING_API.port());
        registry.add("existing.api.read-timeout", () -> "300ms");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetStubsAndCircuitBreaker() {
        EXISTING_API.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        stubExistingApi();
    }

    @AfterAll
    static void stopExistingApi() {
        EXISTING_API.stop();
    }

    @Test
    void should_return_similar_products_in_similarity_order() {
        ResponseEntity<ProductDetail[]> response = restTemplate.getForEntity("/product/1/similar", ProductDetail[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true),
                new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false),
                new ProductDetail("4", "Boots", new BigDecimal("39.99"), true));
    }

    @Test
    void should_omit_product_detail_returning_not_found() {
        ResponseEntity<ProductDetail[]> response = restTemplate.getForEntity("/product/4/similar", ProductDetail[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(
                new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true),
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true));
    }

    @Test
    void should_omit_product_detail_returning_server_error() {
        ResponseEntity<ProductDetail[]> response = restTemplate.getForEntity("/product/5/similar", ProductDetail[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(
                new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true),
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true));
    }

    @Test
    void should_return_404_without_body_when_the_main_product_does_not_exist() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/999/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNullOrEmpty();
    }

    @Test
    void should_serialize_every_product_detail_field_with_id_as_string() {
        ResponseEntity<String> response = restTemplate.getForEntity("/product/1/similar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"id\":\"2\"")
                .contains("\"name\":\"Dress\"")
                .contains("\"price\":19.99")
                .contains("\"availability\":true");
    }

    @Test
    void should_not_count_product_not_found_as_circuit_breaker_failure() {
        for (int i = 0; i < 6; i++) {
            restTemplate.getForEntity("/product/4/similar", ProductDetail[].class);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("productDetail-5");
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void should_omit_product_details_when_the_circuit_breaker_is_open() {
        EXISTING_API.stubFor(get(urlEqualTo("/product/7/similarids"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<ProductDetail[]> lastResponse = null;
        for (int i = 0; i < 6; i++) {
            lastResponse = restTemplate.getForEntity("/product/7/similar", ProductDetail[].class);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("similarIds-7");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastResponse.getBody()).isEmpty();
    }

    @Test
    void should_keep_serving_healthy_products_while_other_products_keep_timing_out() {
        EXISTING_API.stubFor(get(urlEqualTo("/product/9/similarids"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[1000,1001,1002,1003]")));
        for (String slowId : List.of("1000", "1001", "1002", "1003")) {
            EXISTING_API.stubFor(get(urlEqualTo("/product/" + slowId)).willReturn(aResponse().withFixedDelay(600)));
        }
        for (int i = 0; i < 6; i++) {
            restTemplate.getForEntity("/product/9/similar", ProductDetail[].class);
        }

        ResponseEntity<ProductDetail[]> response = restTemplate.getForEntity("/product/1/similar", ProductDetail[].class);

        assertThat(response.getBody()).extracting(ProductDetail::id).containsExactly("2", "3", "4");
    }

    // Under load an open circuit short-circuits thousands of calls at once; none of them may
    // surface as a 5xx.
    @Test
    void should_never_return_5xx_when_the_circuit_breaker_is_open_for_similar_ids() throws Exception {
        EXISTING_API.stubFor(get(urlEqualTo("/product/8/similarids"))
                .willReturn(aResponse().withStatus(500)));
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("similarIds-8");
        circuitBreaker.transitionToOpenState();

        int threads = 40;
        int iterations = 200;
        AtomicInteger nonOkResponses = new AtomicInteger();
        AtomicInteger nonEmptyBodies = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int iteration = 0; iteration < iterations; iteration++) {
                    ResponseEntity<String> response = restTemplate.getForEntity("/product/8/similar", String.class);
                    if (response.getStatusCode() != HttpStatus.OK) {
                        nonOkResponses.incrementAndGet();
                    }
                    if (!"[]".equals(response.getBody())) {
                        nonEmptyBodies.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(nonOkResponses).hasValue(0);
        assertThat(nonEmptyBodies).hasValue(0);
        assertThat(circuitBreaker.getState())
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);

        circuitBreaker.reset();
        EXISTING_API.resetAll();
        stubExistingApi();
        ResponseEntity<String> notFound = restTemplate.getForEntity("/product/999/similar", String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNullOrEmpty();
    }

    // The same similarity graph and failure cases as shared/simulado/mocks.json, minus the slow products.
    private void stubExistingApi() {
        stubJson("/product/1/similarids", "[2,3,4]");
        stubJson("/product/4/similarids", "[1,2,5]");
        stubJson("/product/5/similarids", "[1,2,6]");
        stubJson("/product/1", "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}");
        stubJson("/product/2", "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}");
        stubJson("/product/3", "{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}");
        stubJson("/product/4", "{\"id\":\"4\",\"name\":\"Boots\",\"price\":39.99,\"availability\":true}");
        EXISTING_API.stubFor(get(urlEqualTo("/product/5"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Product not found\"}")));
        EXISTING_API.stubFor(get(urlEqualTo("/product/6"))
                .willReturn(aResponse().withStatus(500)));
        EXISTING_API.stubFor(get(urlEqualTo("/product/999/similarids"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Not Found")));
    }

    private void stubJson(String path, String body) {
        EXISTING_API.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
