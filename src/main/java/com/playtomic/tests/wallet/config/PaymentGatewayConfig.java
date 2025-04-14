package com.playtomic.tests.wallet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(StripeConfigProperties.class)
public class PaymentGatewayConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
