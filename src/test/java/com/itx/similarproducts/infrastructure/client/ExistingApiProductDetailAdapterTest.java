package com.itx.similarproducts.infrastructure.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExistingApiProductDetailAdapterTest {

    private static final String DRESS_JSON = "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}";

    private WireMockServer server;
    private ExistingApiProductDetailAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        adapter = new ExistingApiProductDetailAdapter(RestClients.withReadTimeout(server.port(), Duration.ofMillis(1000)));
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
}
