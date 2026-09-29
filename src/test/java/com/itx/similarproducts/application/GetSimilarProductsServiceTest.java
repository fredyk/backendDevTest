package com.itx.similarproducts.application;

import com.itx.similarproducts.domain.exception.ExistingApiException;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GetSimilarProductsServiceTest {

    private static final ProductDetail SHIRT = new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true);
    private static final ProductDetail DRESS = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    private static final ProductDetail BLAZER = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);
    private static final ProductDetail BOOTS = new ProductDetail("4", "Boots", new BigDecimal("39.99"), true);

    private FakeSimilarProductIdsPort similarProductIdsPort;
    private FakeProductDetailPort productDetailPort;
    private GetSimilarProductsService service;

    @BeforeEach
    void setUp() {
        similarProductIdsPort = new FakeSimilarProductIdsPort();
        productDetailPort = new FakeProductDetailPort();
        service = new GetSimilarProductsService(similarProductIdsPort, productDetailPort);
    }

    @Test
    void should_return_details_in_similar_ids_order() {
        similarProductIdsPort.returnIds("4", "2", "3");
        productDetailPort.returns(DRESS);
        productDetailPort.returns(BLAZER);
        productDetailPort.returns(BOOTS);

        Optional<List<ProductDetail>> result = service.getSimilarProducts("1");

        assertThat(result).contains(List.of(BOOTS, DRESS, BLAZER));
    }

    @Test
    void should_deduplicate_similar_ids_serving_each_product_once() {
        similarProductIdsPort.returnIds("2", "2", "3");
        productDetailPort.returns(DRESS);
        productDetailPort.returns(BLAZER);

        Optional<List<ProductDetail>> result = service.getSimilarProducts("1");

        assertThat(result).contains(List.of(DRESS, BLAZER));
    }

    @Test
    void should_omit_product_detail_returning_not_found_keeping_the_rest() {
        similarProductIdsPort.returnIds("1", "5");
        productDetailPort.returns(SHIRT);
        productDetailPort.fails("5", new ProductNotFoundException("5"));

        Optional<List<ProductDetail>> result = service.getSimilarProducts("4");

        assertThat(result).contains(List.of(SHIRT));
    }

    @Test
    void should_omit_product_detail_failing_with_server_error_keeping_the_rest() {
        similarProductIdsPort.returnIds("2", "6");
        productDetailPort.returns(DRESS);
        productDetailPort.fails("6", new ExistingApiException("server error", new RuntimeException()));

        Optional<List<ProductDetail>> result = service.getSimilarProducts("5");

        assertThat(result).contains(List.of(DRESS));
    }

    @Test
    void should_keep_order_when_some_product_details_are_omitted() {
        similarProductIdsPort.returnIds("2", "3", "4");
        productDetailPort.returns(DRESS);
        productDetailPort.returns(BOOTS);
        productDetailPort.fails("3", new ProductNotFoundException("3"));

        Optional<List<ProductDetail>> result = service.getSimilarProducts("1");

        assertThat(result).contains(List.of(DRESS, BOOTS));
    }

    @Test
    void should_return_empty_when_the_main_product_does_not_exist() {
        similarProductIdsPort.failWith(new ProductNotFoundException("999"));

        Optional<List<ProductDetail>> result = service.getSimilarProducts("999");

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_when_similar_ids_are_empty() {
        similarProductIdsPort.returnIds();

        Optional<List<ProductDetail>> result = service.getSimilarProducts("1");

        assertThat(result).contains(List.of());
    }

    @Test
    void should_return_empty_list_when_similar_ids_port_fails_without_not_found() {
        similarProductIdsPort.failWith(new ExistingApiException("unavailable", new RuntimeException()));

        Optional<List<ProductDetail>> result = service.getSimilarProducts("1");

        assertThat(result).contains(List.of());
    }

    private static final class FakeSimilarProductIdsPort implements SimilarProductIdsPort {

        private List<String> ids = List.of();
        private RuntimeException failure;

        void returnIds(String... ids) {
            this.ids = List.of(ids);
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<String> findSimilarIds(String productId) {
            if (failure != null) {
                throw failure;
            }
            return ids;
        }
    }

    private static final class FakeProductDetailPort implements ProductDetailPort {

        private final Map<String, ProductDetail> details = new HashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();

        void returns(ProductDetail detail) {
            details.put(detail.id(), detail);
        }

        void fails(String productId, RuntimeException failure) {
            failures.put(productId, failure);
        }

        @Override
        public Optional<ProductDetail> findProductDetail(String productId) {
            RuntimeException failure = failures.get(productId);
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(details.get(productId));
        }
    }
}
