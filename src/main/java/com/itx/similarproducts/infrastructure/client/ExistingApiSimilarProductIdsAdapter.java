package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Repository
public class ExistingApiSimilarProductIdsAdapter implements SimilarProductIdsPort {

    private final RestClient restClient;
    private final Cache<String, List<String>> cache;

    public ExistingApiSimilarProductIdsAdapter(RestClient restClient,
                                               @Qualifier("similarProductIdsCache") Cache<String, List<String>> cache) {
        this.restClient = restClient;
        this.cache = cache;
    }

    @Override
    @CircuitBreaker(name = "existingApi", fallbackMethod = "fallbackSimilarIds")
    public List<String> findSimilarIds(String productId) {
        List<String> cached = cache.getIfPresent(productId);
        if (cached != null) {
            return cached;
        }
        List<String> similarIds = requestSimilarIds(productId);
        cache.put(productId, similarIds);
        return similarIds;
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
            throw new ExistingApiException("Failed to fetch similar ids for product " + productId, e);
        }
    }

    // Public on purpose: Resilience4j calls fallbacks by reflection, and on a private method it
    // toggles accessibility per call, which raced under load and let 500s through.
    public List<String> fallbackSimilarIds(String productId, Throwable throwable) {
        if (throwable instanceof ProductNotFoundException productNotFoundException) {
            throw productNotFoundException;
        }
        return List.of();
    }
}
