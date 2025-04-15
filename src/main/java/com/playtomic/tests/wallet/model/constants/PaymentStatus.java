package com.playtomic.tests.wallet.model.constants;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    CREATED,
    SUBMITTED,
    PROCESSING,
    SUCCESSFUL,
    FINALIZED,
    FAILED,
    DENIED,
    CANCELED,
    EXPIRED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            CREATED, Set.of(SUBMITTED, CANCELED, EXPIRED),
            SUBMITTED, Set.of(PROCESSING, FAILED, DENIED, CANCELED, EXPIRED),
            PROCESSING, Set.of(SUCCESSFUL, FAILED, DENIED, EXPIRED),
            SUCCESSFUL, Set.of(FINALIZED),
            FAILED, Set.of(SUBMITTED, CANCELED),
            DENIED, Set.of(),
            CANCELED, Set.of(),
            EXPIRED, Set.of(),
            FINALIZED, Set.of()
    );

    public boolean canTransitionTo(PaymentStatus newStatus) {
        return ALLOWED_TRANSITIONS.get(this).contains(newStatus);
    }

}
