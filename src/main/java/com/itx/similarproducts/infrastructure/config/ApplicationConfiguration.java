package com.itx.similarproducts.infrastructure.config;

import com.itx.similarproducts.application.GetSimilarProductsService;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The service stays free of Spring annotations; this is the one place that wires it.
@Configuration
public class ApplicationConfiguration {

    @Bean
    GetSimilarProductsService getSimilarProductsService(SimilarProductIdsPort similarProductIdsPort,
                                                         ProductDetailPort productDetailPort) {
        return new GetSimilarProductsService(similarProductIdsPort, productDetailPort);
    }
}
