package com.playtomic.tests.wallet.service.impl;

import com.playtomic.tests.wallet.model.exceptions.StripeAmountTooSmallException;
import com.playtomic.tests.wallet.model.exceptions.StripeServiceException;
import com.playtomic.tests.wallet.model.responses.stripe.StripePaymentResponse;
import com.playtomic.tests.wallet.service.gateways.stripe.StripeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StripeServiceTest {

    private URI testChargesUri = URI.create("http://how-would-you-test-me.localhost/charges");
    private URI testRefundsUri = URI.create("http://how-would-you-test-me.localhost/refunds");

    @Mock
    private RestTemplate mockRestTemplate;

    @Mock
    private RestTemplateBuilder mockBuilder;

    private StripeService stripeService;

    @BeforeEach
    public void buildMocks() {
        when(mockBuilder.errorHandler(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockRestTemplate);

        stripeService = new StripeService(testChargesUri, testRefundsUri, mockBuilder);
    }

    @Test
    public void test_exception() {
        when(mockRestTemplate.postForObject(
                eq(testChargesUri),
                any(),
                eq(StripePaymentResponse.class)))
                .thenThrow(new StripeAmountTooSmallException());

        Assertions.assertThrows(StripeAmountTooSmallException.class, () -> {
            stripeService.charge("4242 4242 4242 4242", new BigDecimal(5));
        });
    }

    @Test
    public void test_ok() throws StripeServiceException {
        StripePaymentResponse mockResponse = new StripePaymentResponse("Imagine Id here", BigDecimal.valueOf(15));

        when(mockRestTemplate.postForObject(
                eq(testChargesUri),
                any(),
                eq(StripePaymentResponse.class)))
                .thenReturn(mockResponse);

        stripeService.charge("4242 4242 4242 4242", new BigDecimal(15));
    }
}