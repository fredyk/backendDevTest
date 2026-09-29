package com.itx.similarproducts.application;

import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GetSimilarProductsService {

    private final SimilarProductIdsPort similarProductIdsPort;
    private final ProductDetailPort productDetailPort;

    public GetSimilarProductsService(SimilarProductIdsPort similarProductIdsPort,
                                     ProductDetailPort productDetailPort) {
        this.similarProductIdsPort = similarProductIdsPort;
        this.productDetailPort = productDetailPort;
    }

    /**
     * Details of the products similar to {@code productId}, in similarity order.
     * Empty when the product itself does not exist, which the API turns into a 404.
     */
    public Optional<List<ProductDetail>> getSimilarProducts(String productId) {
        List<String> similarIds;
        try {
            similarIds = similarProductIdsPort.findSimilarIds(productId);
        } catch (ProductNotFoundException e) {
            return Optional.empty();
        } catch (ExistingApiException e) {
            // The product may exist; we just cannot tell which ones are similar right now.
            return Optional.of(List.of());
        }
        List<ProductDetail> details = new ArrayList<>();
        for (String id : similarIds.stream().distinct().toList()) {
            findDetail(id).ifPresent(details::add);
        }
        return Optional.of(List.copyOf(details));
    }

    // A similar product that is missing or failing is left out: one bad neighbour
    // should not cost the client the whole list.
    private Optional<ProductDetail> findDetail(String id) {
        try {
            return productDetailPort.findProductDetail(id);
        } catch (ProductNotFoundException | ExistingApiException e) {
            return Optional.empty();
        }
    }
}
