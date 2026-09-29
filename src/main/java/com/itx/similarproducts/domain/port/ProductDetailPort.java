package com.itx.similarproducts.domain.port;

import com.itx.similarproducts.domain.model.ProductDetail;

import java.util.Optional;

public interface ProductDetailPort {

    Optional<ProductDetail> findProductDetail(String productId);
}
