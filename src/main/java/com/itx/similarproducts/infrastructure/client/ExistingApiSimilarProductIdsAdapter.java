package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;
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

import java.util.List;

@Repository
public class ExistingApiSimilarProductIdsAdapter implements SimilarProductIdsPort {

    private static final Logger log = LoggerFactory.getLogger(ExistingApiSimilarProductIdsAdapter.class);

    private final RestClient restClient;
    private final Cache<String, List<String>> cache;
    private final CircuitBreakerRegistry circuitBreakers;

    public ExistingApiSimilarProductIdsAdapter(RestClient restClient,
                                               @Qualifier("similarProductIdsCache") Cache<String, List<String>> cache,
                                               CircuitBreakerRegistry circuitBreakers) {
        this.restClient = restClient;
        this.cache = cache;
        this.circuitBreakers = circuitBreakers;
    }

    @Override
    public List<String> findSimilarIds(String productId) {
        List<String> cached = cache.getIfPresent(productId);
        if (cached != null) {
            return cached;
        }
        List<String> similarIds = requestThroughCircuitBreaker(productId);
        cache.put(productId, similarIds);
        return similarIds;
    }

    private List<String> requestThroughCircuitBreaker(String productId) {
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker("similarIds-" + productId);
        try {
            return circuitBreaker.executeSupplier(() -> requestSimilarIds(productId));
        } catch (CallNotPermittedException e) {
            throw new ExistingApiException("Circuit open for similar ids of product " + productId, e);
        }
    }

    private List<String> requestSimilarIds(String productId) {
        try {
            // The API sends numbers ([2,3,4]); reading them as strings keeps ids opaque to us.
            String[] ids = restClient.get()
                    .uri("/product/{productId}/similarids", productId)
                    .retrieve()
                    .body(String[].class);
            return ids == null ? List.of() : List.of(ids);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId, e);
        } catch (RestClientException e) {
            log.warn("Failed to fetch similar ids for product {}: {}", productId, e.getMessage());
            throw new ExistingApiException("Failed to fetch similar ids for product " + productId, e);
        }
    }
}
