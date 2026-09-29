package com.itx.similarproducts.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "existing.api")
public record ExistingApiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
