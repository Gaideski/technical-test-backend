package com.playtomic.tests.wallet.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.Name;

import java.net.URI;

@Getter
@ConfigurationProperties(prefix = "payment-gateways.stripe.simulator")
public class StripeConfigProperties {
    private final URI chargesUri;
    private final URI refundsUri;

    @ConstructorBinding
    public StripeConfigProperties(
            @Name("charges-uri") URI chargesUri,
            @Name("refunds-uri") URI refundsUri) {
        this.chargesUri = chargesUri;
        this.refundsUri = refundsUri;
    }

}