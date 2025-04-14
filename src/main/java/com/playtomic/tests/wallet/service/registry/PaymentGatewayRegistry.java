package com.playtomic.tests.wallet.service.registry;

import com.playtomic.tests.wallet.model.annotation.PaymentService;
import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.gateways.IPaymentsService;
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

    @Autowired
    public PaymentGatewayRegistry(ApplicationContext applicationContext) {
        this.paymentsProviderMap = new EnumMap<>(PaymentGateway.class);

        // Get all beans that implement IPaymentsService
        Map<String, IPaymentsService> paymentServices =
                applicationContext.getBeansOfType(IPaymentsService.class);

        paymentServices.forEach((beanName, service) -> {
            // Get the target class (handling Spring proxies)
            Class<?> targetClass = AopUtils.isAopProxy(service) ?
                    AopUtils.getTargetClass(service) : service.getClass();

            // Use AnnotationUtils to find annotation even in proxied classes
            PaymentService annotation = AnnotationUtils.findAnnotation(
                    targetClass, PaymentService.class);

            if (annotation != null) {
                paymentsProviderMap.put(annotation.value(),
                        new GatewayConnection(service, null, null));
            }
        });
    }

    public GatewayConnection getProviderConnection(PaymentGateway provider) {
        return paymentsProviderMap.get(provider);
    }


}
