package com.playtomic.tests.wallet.service.gateways.stripe;

import com.playtomic.tests.wallet.config.StripeConfigProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StripeConfigProperties.class)
public class StripeConfiguration {

    private final StripeConfigProperties properties;

    public StripeConfiguration(StripeConfigProperties properties) {
        this.properties = properties;
    }

    @Bean
    public URI stripeChargesUri() {
        return properties.getChargesUri();
    }

    @Bean
    public URI stripeRefundsUri() {
        return properties.getRefundsUri();
    }
}