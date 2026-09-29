package com.itx.similarproducts.infrastructure.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.itx.similarproducts.domain.model.ProductDetail;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsControllerIT {

    // Started statically so its port is known before the Spring context reads existing.api.base-url.
    private static final WireMockServer EXISTING_API = new WireMockServer(options().dynamicPort());

    static {
        EXISTING_API.start();
    }

    @DynamicPropertySource
    static void existingApiProperties(DynamicPropertyRegistry registry) {
        registry.add("existing.api.base-url", () -> "http://localhost:" + EXISTING_API.port());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetStubs() {
        EXISTING_API.resetAll();
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
