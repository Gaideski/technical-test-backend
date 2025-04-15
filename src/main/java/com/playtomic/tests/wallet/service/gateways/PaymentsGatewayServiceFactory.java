package com.playtomic.tests.wallet.service.gateways;

import com.playtomic.tests.wallet.config.StripeConfigProperties;
import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.service.gateways.stripe.StripeService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;


@Service
public class PaymentsGatewayServiceFactory {

    private final StripeConfigProperties stripeConfig;
    private final RestTemplateBuilder restTemplateBuilder;


    public PaymentsGatewayServiceFactory(StripeConfigProperties stripeConfig, RestTemplateBuilder restTemplateBuilder) {
        this.stripeConfig = stripeConfig;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    public IPaymentGatewayService createPaymentsService(PaymentGateway provider) {
        switch (provider) {
            case STRIPE:
                return createStripeService();

            default:
                return null;
        }

    }

    private IPaymentGatewayService createStripeService() {
        return new StripeService(
                stripeConfig.getChargesUri(),
                stripeConfig.getRefundsUri(),
                restTemplateBuilder);
    }
}
