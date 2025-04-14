package com.playtomic.tests.wallet.utils;

import com.playtomic.tests.wallet.model.requests.PaymentRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.playtomic.tests.wallet.utils.cardUtils.bytesToHex;
import static com.playtomic.tests.wallet.utils.cardUtils.maskCardNumber;

public class IdempotencyUtils {

    public static String generateIdempotencykey(PaymentRequest request) {
        String rawKey = String.format(
                "%s-%s-%s-%s",
                request.getAccountId(),
                maskCardNumber(request.getCardNumber()),
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
