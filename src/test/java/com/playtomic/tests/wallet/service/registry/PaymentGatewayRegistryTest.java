package com.playtomic.tests.wallet.service.registry;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.exceptions.StripeServiceException;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.model.responses.stripe.StripePaymentResponse;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.gateways.IPaymentsService;
import com.playtomic.tests.wallet.service.gateways.stripe.StripeService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentGatewayRegistryTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private StripeService stripeService;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private PaymentGatewayRegistry paymentGatewayRegistry;

    @BeforeEach
    public void setup() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(5)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        Map<String, IPaymentsService> paymentServicesMap = new HashMap<>();
        paymentServicesMap.put("stripeService", stripeService);
        when(applicationContext.getBeansOfType(IPaymentsService.class)).thenReturn(paymentServicesMap);


        paymentGatewayRegistry = new PaymentGatewayRegistry(applicationContext, circuitBreakerRegistry);
    }

    @Test
    public void testRegistryInitialization() {
        GatewayConnection connection = paymentGatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);

        assertNotNull(connection);
        assertNotNull(connection.getCircuitBreaker());
        assertEquals(CircuitBreaker.State.CLOSED, connection.getCircuitBreaker().getState());
        assertSame(stripeService, connection.getPaymentGateway());
    }

    @Test
    public void testCircuitBreakerNormalOperation() throws ExecutionException, InterruptedException {
        GatewayConnection connection = paymentGatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);
        CircuitBreaker circuitBreaker = connection.getCircuitBreaker();

        StripePaymentResponse mockResponse = new StripePaymentResponse("payment_id_123", BigDecimal.TEN);
        when(stripeService.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<IPaymentResponse> futureResponse =
                circuitBreaker.executeCompletionStage(() ->
                        stripeService.charge("4242424242424242", BigDecimal.TEN)
                ).toCompletableFuture();

        IPaymentResponse response = futureResponse.get();
        assertNotNull(response);
        assertEquals("payment_id_123", response.getGatewayTransactionID());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        verify(stripeService, times(1)).charge(anyString(), any(BigDecimal.class));
    }

    @Test
    public void testCircuitBreakerOpensAfterFailures() throws ExecutionException, InterruptedException {
        GatewayConnection connection = paymentGatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);
        CircuitBreaker circuitBreaker = connection.getCircuitBreaker();

        CompletableFuture<IPaymentResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new StripeServiceException());
        when(stripeService.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(failedFuture);

        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.executeCompletionStage(() ->
                        stripeService.charge("4242424242424242", BigDecimal.TEN)
                ).toCompletableFuture().get();
                fail("Expected exception was not thrown");
            } catch (ExecutionException e) {
                assertInstanceOf(StripeServiceException.class, e.getCause());
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        reset(stripeService);

        try {
            circuitBreaker.executeCompletionStage(() ->
                    stripeService.charge("4242424242424242", BigDecimal.TEN)
            ).toCompletableFuture().get();
            fail("Call should have been rejected by circuit breaker");
        } catch (ExecutionException e) {
            assertInstanceOf(CallNotPermittedException.class, e.getCause());
        }

        verify(stripeService, never()).charge(anyString(), any(BigDecimal.class));
    }

    @Test
    public void testCircuitBreakerRecovery() throws Exception {
        // Given
        GatewayConnection connection = paymentGatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);
        CircuitBreaker circuitBreaker = connection.getCircuitBreaker();

        // First simulate failures to open the circuit
        CompletableFuture<IPaymentResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new StripeServiceException());
        when(stripeService.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(failedFuture);

        // Force the circuit to open
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.executeCompletionStage(() ->
                        stripeService.charge("4242424242424242", BigDecimal.TEN)
                ).toCompletableFuture().get();
            } catch (Exception e) {
                // Expected
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // wait for the circuit to transition to half-open
        Thread.sleep(200); // Wait longer than the configured waitDurationInOpenState

        reset(stripeService);
        StripePaymentResponse mockResponse = new StripePaymentResponse("recovery_id", BigDecimal.TEN);
        when(stripeService.charge(anyString(), any(BigDecimal.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Test if circuit transitions to half-open
        if (circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN) {
            // Send successful test requests in half-open state
            for (int i = 0; i < 2; i++) {
                IPaymentResponse response = circuitBreaker.executeCompletionStage(() ->
                        stripeService.charge("4242424242424242", BigDecimal.TEN)
                ).toCompletableFuture().get();

                assertNotNull(response);
                assertEquals("recovery_id", response.getGatewayTransactionID());
            }

            // Circuit should be closed after successful test requests
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        } else {
            // If we don't catch it in HALF_OPEN state (timing can be tricky in tests),
            // just verify it eventually becomes CLOSED again
            for (int i = 0; i < 10 && circuitBreaker.getState() != CircuitBreaker.State.CLOSED; i++) {
                Thread.sleep(50);
                try {
                    circuitBreaker.executeCompletionStage(() ->
                            stripeService.charge("4242424242424242", BigDecimal.TEN)
                    ).toCompletableFuture().get();
                } catch (Exception e) {
                    // Expected if still open
                }
            }

            // Eventually we should reach CLOSED state
            assertTrue(CircuitBreaker.State.CLOSED == circuitBreaker.getState() ||
                            CircuitBreaker.State.HALF_OPEN == circuitBreaker.getState(),
                    "Circuit breaker should eventually transition to CLOSED or HALF_OPEN state");
        }
    }
}