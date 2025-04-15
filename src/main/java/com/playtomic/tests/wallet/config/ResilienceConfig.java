package com.playtomic.tests.wallet.config;


import com.playtomic.tests.wallet.model.exceptions.StripeAmountTooSmallException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Configure default circuit breaker settings
        // This should be externalized using var env or conf files
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .recordException(throwable -> {
                    // Don't record StripeAmountTooSmallException as a failure
                    if (throwable instanceof StripeAmountTooSmallException ||
                            (throwable.getCause() != null && throwable.getCause() instanceof StripeAmountTooSmallException)) {
                        return false;
                    }
                    // Record all other exceptions as failures
                    return true;
                })
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
