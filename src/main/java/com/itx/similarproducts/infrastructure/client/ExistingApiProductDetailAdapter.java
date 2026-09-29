package com.itx.similarproducts.infrastructure.client;

import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
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
}
