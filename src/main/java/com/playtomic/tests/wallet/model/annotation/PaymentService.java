package com.playtomic.tests.wallet.model.annotation;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PaymentService {
    PaymentGateway value();
}
