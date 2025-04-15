package com.playtomic.tests.wallet.service.gateways;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GatewayConnection {
    // This class holds the connection with the gateway, also contains the circuit-breaker
    // To be added: retry mechanism
    PaymentGateway paymentGateway;
    IPaymentGatewayService paymentGatewayService;
    CircuitBreaker circuitBreaker;
}
