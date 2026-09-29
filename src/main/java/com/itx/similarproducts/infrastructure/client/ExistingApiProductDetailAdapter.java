package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Repository
public class ExistingApiProductDetailAdapter implements ProductDetailPort {

    private static final Logger log = LoggerFactory.getLogger(ExistingApiProductDetailAdapter.class);

    private final RestClient restClient;
    private final Cache<String, ProductDetail> cache;
    private final CircuitBreakerRegistry circuitBreakers;

    public ExistingApiProductDetailAdapter(RestClient restClient,
                                           @Qualifier("productDetailCache") Cache<String, ProductDetail> cache,
                                           CircuitBreakerRegistry circuitBreakers) {
        this.restClient = restClient;
        this.cache = cache;
        this.circuitBreakers = circuitBreakers;
    }

    @Override
    public Optional<ProductDetail> findProductDetail(String productId) {
        // The cache sits in front of the breaker: a detail we already have is served even
        // while that product's circuit is open.
        ProductDetail cached = cache.getIfPresent(productId);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Only successes are stored: a 404 or a failure throws before reaching put.
        ProductDetail detail = requestThroughCircuitBreaker(productId);
        if (detail == null) {
            return Optional.empty();
        }
        cache.put(productId, detail);
        return Optional.of(detail);
    }

    // One breaker per product: a product that always times out opens its own circuit
    // and stops costing a timeout, without taking healthy products down with it.
    private ProductDetail requestThroughCircuitBreaker(String productId) {
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker("productDetail-" + productId);
        try {
            return circuitBreaker.executeSupplier(() -> requestProductDetail(productId));
        } catch (CallNotPermittedException e) {
            throw new ExistingApiException("Circuit open for product detail " + productId, e);
        }
    }

    private ProductDetail requestProductDetail(String productId) {
        try {
            return restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetail.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId, e);
        } catch (RestClientException e) {
            // Read timeouts land here too: the JDK client wraps them in a ResourceAccessException.
            // Only real upstream failures are logged: a 404 is an answer, and an open circuit
            // is already reported once, when it opens.
            log.warn("Failed to fetch product detail for product {}: {}", productId, e.getMessage());
            throw new ExistingApiException("Failed to fetch product detail for product " + productId, e);
        }
    }
}
