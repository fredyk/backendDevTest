package com.itx.similarproducts.infrastructure.controller;

import com.itx.similarproducts.application.GetSimilarProductsService;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SimilarProductsController {

    private final GetSimilarProductsService getSimilarProductsService;

    public SimilarProductsController(GetSimilarProductsService getSimilarProductsService) {
        this.getSimilarProductsService = getSimilarProductsService;
    }

    @GetMapping("/product/{productId}/similar")
    public ResponseEntity<List<ProductDetail>> getSimilarProducts(@PathVariable String productId) {
        return getSimilarProductsService.getSimilarProducts(productId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
