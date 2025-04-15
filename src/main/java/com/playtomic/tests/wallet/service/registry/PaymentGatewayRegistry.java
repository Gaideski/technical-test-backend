package com.playtomic.tests.wallet.service.registry;

import com.playtomic.tests.wallet.model.annotation.PaymentService;
import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.gateways.IPaymentsService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class PaymentGatewayRegistry {
    private final Map<PaymentGateway, GatewayConnection> paymentsProviderMap;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    public PaymentGatewayRegistry(ApplicationContext applicationContext, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.paymentsProviderMap = new EnumMap<>(PaymentGateway.class);


        Map<String, IPaymentsService> paymentServices =
                applicationContext.getBeansOfType(IPaymentsService.class);

        paymentServices.forEach((beanName, service) -> {
            Class<?> targetClass = AopUtils.isAopProxy(service) ?
                    AopUtils.getTargetClass(service) : service.getClass();

            PaymentService annotation = AnnotationUtils.findAnnotation(
                    targetClass, PaymentService.class);

            if (annotation != null) {
                CircuitBreaker circuitBreaker = this.circuitBreakerRegistry.circuitBreaker(
                        annotation.value().name() + "-circuit-breaker");

                paymentsProviderMap.put(annotation.value(),
                        new GatewayConnection(service, circuitBreaker, null));
            }
        });
    }

    public GatewayConnection getProviderConnection(PaymentGateway provider) {
        return paymentsProviderMap.get(provider);
    }


}
