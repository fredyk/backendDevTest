package com.itx.similarproducts.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerLoggingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLoggingConfiguration.class);

    // Breakers are created per product at runtime, so the listener is attached to each one as the
    // registry creates it. A state change is the signal worth a line in the log, not every call.
    @Bean
    RegistryEventConsumer<CircuitBreaker> circuitBreakerStateLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher().onStateTransition(transition ->
                        log.warn("Circuit breaker {} changed from {} to {}",
                                transition.getCircuitBreakerName(),
                                transition.getStateTransition().getFromState(),
                                transition.getStateTransition().getToState()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
    }
}
