package com.playtomic.tests.wallet.service.impl;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.exceptions.InvalidTransactionStatusException;
import com.playtomic.tests.wallet.model.exceptions.StripeServiceException;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.model.responses.stripe.StripePaymentResponse;
import com.playtomic.tests.wallet.service.TransactionService;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.gateways.IPaymentGatewayService;
import com.playtomic.tests.wallet.service.registry.PaymentGatewayRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceCircuitBreakerTest {

    @Mock
    private PaymentGatewayRegistry gatewayRegistry;

    @Mock
    private IPaymentGatewayService paymentGateway;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private TransactionService transactionService;


    private PaymentService paymentService;
    private PaymentRequest paymentRequest;
    private GatewayConnection gatewayConnection;

    @BeforeEach
    public void setup() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .writableStackTraceEnabled(true) // Important for this error
                .build();


        when(circuitBreaker.executeCompletionStage(any()))
                .thenAnswer(invocation -> {
                    Supplier<CompletionStage<IPaymentResponse>> supplier = invocation.getArgument(0);
                    return supplier.get();
                });

        gatewayConnection = new GatewayConnection(PaymentGateway.STRIPE,paymentGateway, circuitBreaker);
        when(gatewayRegistry.getProviderConnection(eq(PaymentGateway.STRIPE))).thenReturn(gatewayConnection);

        paymentService = new PaymentService(gatewayRegistry, transactionService);

        paymentRequest = new PaymentRequest(
                "user_abc123",
                "4242424242424242",
                BigDecimal.valueOf(50.0),
                "session:4446:"
        );
    }

    @Test
    public void testPaymentSuccessWithCircuitBreaker() throws ExecutionException, InterruptedException, TransactionNotFoundException, InvalidTransactionStatusException {
        long transactionId = 123L;
        StripePaymentResponse mockResponse = new StripePaymentResponse("payment_123", BigDecimal.valueOf(50.0));

        when(paymentGateway.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<IPaymentResponse> result = paymentService.requestPayment(paymentRequest, transactionId);
        IPaymentResponse response = result.get();

        assertNotNull(response);
        assertEquals("payment_123", response.getGatewayTransactionID());
        verify(transactionService).updateTransactionPaymentStatus(eq(123L), eq(PaymentStatus.SUCCESSFUL));
    }

    @Test
    public void testPaymentFailureWithCircuitBreaker() {
        long transactionId = 123L;
        StripeServiceException stripeException = new StripeServiceException();

        when(paymentGateway.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(CompletableFuture.failedFuture(stripeException));

        CompletableFuture<IPaymentResponse> result = paymentService.requestPayment(paymentRequest, transactionId);

        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(StripeServiceException.class, exception.getCause());
        try {
            verify(transactionService).updateTransactionPaymentStatus(eq(123L), eq(PaymentStatus.FAILED));
        } catch (TransactionNotFoundException | InvalidTransactionStatusException e) {
            fail("Transaction service should be called with fallback status");
        }
    }

    @Test
    public void testCircuitBreakerRejection() {
        long transactionId = 123L;

        CallNotPermittedException circuitBreakerOpen = CallNotPermittedException
                .createCallNotPermittedException(circuitBreaker);

        when(circuitBreaker.executeCompletionStage(any()))
                .thenAnswer(invocation -> {
                    return CompletableFuture.failedFuture(circuitBreakerOpen);
                });

        CompletableFuture<IPaymentResponse> result = paymentService.requestPayment(paymentRequest, transactionId);

        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(CallNotPermittedException.class, exception.getCause());

        verify(paymentGateway, never()).charge(anyString(), any(BigDecimal.class));
    }

    public static class PaymentService {
        private final PaymentGatewayRegistry gatewayRegistry;
        private final TransactionService transactionService;

        public PaymentService(PaymentGatewayRegistry gatewayRegistry, TransactionService transactionService) {
            this.gatewayRegistry = gatewayRegistry;
            this.transactionService = transactionService;
        }

        public CompletableFuture<IPaymentResponse> requestPayment(PaymentRequest paymentRequest, long transactionId) {
            GatewayConnection conn = gatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);

            return conn.getCircuitBreaker().executeCompletionStage(
                            () -> conn.getPaymentGatewayService().charge(paymentRequest.getCardNumber(), paymentRequest.getAmount())
                    ).toCompletableFuture()
                    .thenApply(response -> {
                        try {
                            transactionService.updateTransactionPaymentStatus(transactionId, PaymentStatus.SUCCESSFUL);
                            return response;
                        } catch (TransactionNotFoundException | InvalidTransactionStatusException e) {
                            throw new CompletionException("Transaction update failed", e);
                        }
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;

                        try {
                            transactionService.updateTransactionPaymentStatus(transactionId, PaymentStatus.FAILED);
                        } catch (TransactionNotFoundException | InvalidTransactionStatusException ignored) {
                        }

                        throw new CompletionException("Payment processing failed", cause);
                    });
        }
    }
}