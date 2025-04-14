package com.playtomic.tests.wallet.service.gateways;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GatewayConnection {
    // This class holds the connection with the gateway, also contains the circuit-breaker and retries mechanisms
    IPaymentsService paymentGateway;
    Object circuitBreaker;
    Object retrier;
}
