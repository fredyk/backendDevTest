package com.itx.similarproducts.infrastructure.config;

import com.itx.similarproducts.application.GetSimilarProductsService;
import com.itx.similarproducts.domain.port.ProductDetailPort;
import com.itx.similarproducts.domain.port.SimilarProductIdsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// The service stays free of Spring annotations; this is the one place that wires it.
@Configuration
public class ApplicationConfiguration {

    // One virtual thread per detail request: blocking I/O costs almost nothing, so no pool to size.
    @Bean(destroyMethod = "close")
    ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    GetSimilarProductsService getSimilarProductsService(SimilarProductIdsPort similarProductIdsPort,
                                                         ProductDetailPort productDetailPort,
                                                         ExecutorService virtualThreadExecutor) {
        return new GetSimilarProductsService(similarProductIdsPort, productDetailPort, virtualThreadExecutor);
    }
}
