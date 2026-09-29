package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Repository
public class ExistingApiProductDetailAdapter implements ProductDetailPort {

    private final RestClient restClient;
    private final Cache<String, ProductDetail> cache;

    public ExistingApiProductDetailAdapter(RestClient restClient,
                                           @Qualifier("productDetailCache") Cache<String, ProductDetail> cache) {
        this.restClient = restClient;
        this.cache = cache;
    }

    @Override
    @CircuitBreaker(name = "existingApi", fallbackMethod = "fallbackProductDetail")
    public Optional<ProductDetail> findProductDetail(String productId) {
        ProductDetail cached = cache.getIfPresent(productId);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Only successes are stored: a 404 or a failure throws before reaching put.
        ProductDetail detail = requestProductDetail(productId);
        if (detail == null) {
            return Optional.empty();
        }
        cache.put(productId, detail);
        return Optional.of(detail);
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
            throw new ExistingApiException("Failed to fetch product detail for product " + productId, e);
        }
    }

    // A 404 is an answer, not an outage: it goes back to the caller untouched.
    public Optional<ProductDetail> fallbackProductDetail(String productId, Throwable throwable) {
        if (throwable instanceof ProductNotFoundException productNotFoundException) {
            throw productNotFoundException;
        }
        return Optional.empty();
    }
}
