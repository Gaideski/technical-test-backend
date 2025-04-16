package com.playtomic.tests.wallet.utils;

import com.playtomic.tests.wallet.model.requests.PaymentRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.playtomic.tests.wallet.utils.CardUtils.bytesToHex;

public class IdempotencyUtils {


    // Time window for temporal idempotency (e.g., 5 minutes)
    private static final int IDEMPOTENCY_WINDOW_MINUTES = 5;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                    .withZone(ZoneId.of("UTC"));

    public static String generateIdempotenceKey(PaymentRequest request) {
        // Use explicit client-provided idempotency key if available
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
            return request.getIdempotencyKey();
        }

        String timeWindow = TIME_FORMATTER.format(
                Instant.now().minusSeconds(60 * IDEMPOTENCY_WINDOW_MINUTES)
        );

        String rawKey = String.format(
                "%s|%s|%s|%s|%s|%s|%s|%s",
                request.getAccountId(),
                request.getAmount().stripTrailingZeros().toPlainString(), // Normalized amount
                request.getSessionId(),
                request.getCardNumber().substring(0, 6), // First 6 digits (BIN)
                request.getCardNumber().substring(Math.max(0, request.getCardNumber().length() - 4)),
                request.getPaymentMethod(),
                request.getPaymentType(),
                timeWindow
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate idempotency key", e);
        }

    }
}
