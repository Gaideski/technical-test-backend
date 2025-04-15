package com.playtomic.tests.wallet.service.gateways;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GatewayConnection {
    // This class holds the connection with the gateway, also contains the circuit-breaker and retries mechanisms
    IPaymentsService paymentGateway;
    CircuitBreaker circuitBreaker;
    Object retrier;
}
