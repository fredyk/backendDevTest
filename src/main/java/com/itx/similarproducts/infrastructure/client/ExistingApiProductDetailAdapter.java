package com.itx.similarproducts.infrastructure.client;

import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Repository
public class ExistingApiProductDetailAdapter implements ProductDetailPort {

    private final RestClient restClient;

    public ExistingApiProductDetailAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = "existingApi", fallbackMethod = "fallbackProductDetail")
    public Optional<ProductDetail> findProductDetail(String productId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/product/{productId}", productId)
                    .retrieve()
                    .body(ProductDetail.class));
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
