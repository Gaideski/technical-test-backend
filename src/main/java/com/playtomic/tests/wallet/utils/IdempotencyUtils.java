package com.playtomic.tests.wallet.utils;

import com.playtomic.tests.wallet.model.requests.PaymentRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.playtomic.tests.wallet.utils.CardUtils.bytesToHex;

public class IdempotencyUtils {

    public static String generateIdempotenceKey(PaymentRequest request) {
        String rawKey = String.format(
                "%s-%s-%s",
                request.getAccountId(),
                request.getAmount(),
                request.getSessionId()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate idempotency key", e);
        }
    }
}
