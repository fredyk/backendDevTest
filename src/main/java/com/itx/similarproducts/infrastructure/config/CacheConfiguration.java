package com.itx.similarproducts.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CacheConfiguration {

    @Bean
    Cache<String, List<String>> similarProductIdsCache(ExistingApiProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(10_000)
                .build();
    }

    @Bean
    Cache<String, ProductDetail> productDetailCache(ExistingApiProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(10_000)
                .build();
    }
}
