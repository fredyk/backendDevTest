package com.itx.similarproducts.application;

import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class GetSimilarProductsService {

    private final SimilarProductIdsPort similarProductIdsPort;
    private final ProductDetailPort productDetailPort;
    private final ExecutorService executor;

    public GetSimilarProductsService(SimilarProductIdsPort similarProductIdsPort,
                                     ProductDetailPort productDetailPort,
                                     ExecutorService executor) {
        this.similarProductIdsPort = similarProductIdsPort;
        this.productDetailPort = productDetailPort;
        this.executor = executor;
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
        // All requests go out at once; collecting the futures in submission order keeps
        // the similarity order, so the response takes as long as the slowest detail.
        List<Future<Optional<ProductDetail>>> futures = similarIds.stream()
                .distinct()
                .map(id -> executor.submit(() -> findDetail(id)))
                .toList();
        List<ProductDetail> details = new ArrayList<>();
        for (Future<Optional<ProductDetail>> future : futures) {
            resolve(future).ifPresent(details::add);
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

    private Optional<ProductDetail> resolve(Future<Optional<ProductDetail>> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
