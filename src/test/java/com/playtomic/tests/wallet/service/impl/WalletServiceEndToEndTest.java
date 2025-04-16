package com.playtomic.tests.wallet.service.impl;

import com.playtomic.tests.wallet.model.dto.Wallet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WalletServiceEndToEndTest {

    private static final Logger logger = LoggerFactory.getLogger(WalletServiceEndToEndTest.class);
    private static final String BASE_URL = "http://localhost:";
    private static final String WALLET_API = "/api/wallet/";
    private static final String RECHARGE_API = "/api/wallet/recharge";
    private static final int NUM_USERS = 10;
    private static final int NUM_INVALID_USERS = 5;
    // Timeout and polling configuration
    private static final int MAX_POLL_ATTEMPTS = 15;
    private static final int POLL_INTERVAL_MS = 500;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();
    @LocalServerPort
    private int port;

    @Test
    public void testConcurrentRecharges() throws Exception {
        // 1. Create session IDs for our test (must be at least 10 chars)
        List<String> sessionIds = IntStream.range(0, NUM_USERS)
                .mapToObj(i -> "session-" + UUID.randomUUID().toString())
                .collect(Collectors.toList());

        // 2. Create valid account IDs (must be at least 5 chars)
        List<String> validAccountIds = IntStream.range(0, NUM_USERS)
                .mapToObj(i -> "test-account-" + i)
                .collect(Collectors.toList());

        // 3. Also create some invalid account IDs that don't exist
        List<String> invalidAccountIds = IntStream.range(0, NUM_INVALID_USERS)
                .mapToObj(i -> "nonexistent-account-" + i)
                .collect(Collectors.toList());

        // 4. Create or retrieve wallets for all valid accounts and track initial balances
        Map<String, Wallet> initialWallets = new HashMap<>();
        for (int i = 0; i < validAccountIds.size(); i++) {
            String accountId = validAccountIds.get(i);
            String sessionId = sessionIds.get(i);
            initialWallets.put(accountId, getOrCreateWallet(accountId, sessionId));
            logger.info("Initial balance for {}: {}", accountId,
                    initialWallets.get(accountId) != null ? initialWallets.get(accountId).getFunds() : "null");
        }

        logger.info("Created/Retrieved {} wallets", initialWallets.size());

        // Track expected changes for each account
        ConcurrentMap<String, BigDecimal> expectedValidRecharges = new ConcurrentHashMap<>();

        // List to track small amount recharges for validation
        ConcurrentMap<String, List<BigDecimal>> smallAmountRequests = new ConcurrentHashMap<>();

        // 5. Prepare a mix of valid and invalid requests
        List<Callable<List<RechargeResult>>> requests = new ArrayList<>();

        // Valid recharges with amounts >= 10
        for (int i = 0; i < NUM_USERS * 3; i++) {
            String accountId = validAccountIds.get(random.nextInt(validAccountIds.size()));
            String sessionId = initialWallets.get(accountId) != null ?
                    sessionIds.get(validAccountIds.indexOf(accountId)) :
                    "session-" + UUID.randomUUID();

            BigDecimal amount = BigDecimal.valueOf(10 + random.nextInt(990));
            String cardNumber = "4111111111111111"; // Test card number

            int finalI = i;
            requests.add(() -> {
                List<RechargeResult> results = new ArrayList<>();
                try {
                    // Get initial balance
                    Wallet walletBefore = getOrCreateWallet(accountId, sessionId);
                    BigDecimal initialBalance = walletBefore != null ? walletBefore.getFunds() : BigDecimal.ZERO;

                    // Create the request body as a Map with the correct JSON field names
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("account_id", accountId);
                    requestBody.put("credit_card", cardNumber);
                    requestBody.put("amount", amount);
                    requestBody.put("session_id", sessionId);

                    ResponseEntity<?> response = performRechargeWithMap(requestBody);
                    logger.info("Recharge request {} sent: {} - {}", finalI, accountId, amount);
                    assertEquals(202, response.getStatusCode().value());

                    // Track the expected recharge amount
                    expectedValidRecharges.compute(accountId, (k, v) -> (v == null) ? amount : v.add(amount));

                    // Wait and verify the balance was updated
                    boolean verified = waitForBalanceUpdate(accountId, sessionId, initialBalance, amount);

                    results.add(new RechargeResult(
                            accountId,
                            amount,
                            true,
                            verified ? "Balance verified" : "Balance not updated after timeout",
                            verified));

                } catch (Exception e) {
                    logger.error("Error during valid recharge {}: {}", finalI, e.getMessage());
                    results.add(new RechargeResult(accountId, amount, false, e.getMessage(), false));
                }
                return results;
            });
        }

        // Invalid recharges - amount too small (< 10)
        for (int i = 0; i < NUM_USERS * 2; i++) {
            String accountId = validAccountIds.get(random.nextInt(NUM_USERS));
            String sessionId = initialWallets.get(accountId) != null ?
                    sessionIds.get(validAccountIds.indexOf(accountId)) :
                    "session-" + UUID.randomUUID();

            BigDecimal amount = BigDecimal.valueOf(0.01 + random.nextDouble() * 9.98).setScale(2, BigDecimal.ROUND_HALF_UP);
            String cardNumber = "4111111111111111"; // Test card number

            // Track small amount requests for this account
            smallAmountRequests.computeIfAbsent(accountId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(amount);

            int finalI = i;
            requests.add(() -> {
                List<RechargeResult> results = new ArrayList<>();
                try {
                    // Get initial balance
                    Wallet walletBefore = getOrCreateWallet(accountId, sessionId);
                    BigDecimal initialBalance = walletBefore != null ? walletBefore.getFunds() : BigDecimal.ZERO;

                    // Create the request body as a Map with the correct JSON field names
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("account_id", accountId);
                    requestBody.put("credit_card", cardNumber);
                    requestBody.put("amount", amount);
                    requestBody.put("session_id", sessionId);

                    ResponseEntity<?> response = performRechargeWithMap(requestBody);
                    logger.info("Small amount recharge {} sent: {} - {}", finalI, accountId, amount);

                    // For small amounts, we don't check immediately - we'll do a consolidated check at the end
                    results.add(new RechargeResult(
                            accountId,
                            amount,
                            true,
                            "Small amount recharge accepted - will verify at end",
                            true));

                } catch (Exception e) {
                    // Small amount might be rejected at API level
                    logger.info("Expected error for small amount {} recharge: {} - {}", finalI, amount, e.getMessage());
                    results.add(new RechargeResult(accountId, amount, false, e.getMessage(), true));
                }
                return results;
            });
        }

        // Invalid recharges - non-existent accounts
        for (int i = 0; i < NUM_INVALID_USERS * 2; i++) {
            String accountId = invalidAccountIds.get(random.nextInt(invalidAccountIds.size()));
            String sessionId = "session-" + UUID.randomUUID();
            BigDecimal amount = BigDecimal.valueOf(10 + random.nextInt(990));
            String cardNumber = "4111111111111111"; // Test card number

            int finalI = i;
            requests.add(() -> {
                List<RechargeResult> results = new ArrayList<>();
                try {
                    // Create the request body as a Map with the correct JSON field names
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("account_id", accountId);
                    requestBody.put("credit_card", cardNumber);
                    requestBody.put("amount", amount);
                    requestBody.put("session_id", sessionId);

                    performRechargeWithMap(requestBody);
                    logger.info("Unexpected success for invalid account {} recharge", finalI);
                    results.add(new RechargeResult(accountId, amount, true, "Unexpected success for invalid account", false));
                } catch (HttpClientErrorException e) {
                    // Expected behavior - not found
                    logger.info("Expected error for invalid account {} recharge: {} - {}", finalI, accountId, e.getMessage());
                    assertEquals(404, e.getStatusCode().value(), "Expected 404 for invalid account");
                    results.add(new RechargeResult(accountId, amount, false, e.getMessage(), true));
                } catch (Exception e) {
                    logger.error("Unexpected error type for invalid account {} recharge: {}", finalI, e.getMessage());
                    results.add(new RechargeResult(accountId, amount, false, e.getMessage(), false));
                }
                return results;
            });
        }

        // Duplicate requests (idempotency test) - FIXED to run sequentially in the same callable
        for (int i = 0; i < NUM_USERS; i++) {
            String accountId = validAccountIds.get(i);
            String sessionId = sessionIds.get(i);
            BigDecimal amount = BigDecimal.valueOf(50);
            String cardNumber = "4111111111111111";

            String explicitIdempotencyKey = "test-idempotency-key-" + accountId + "-" + UUID.randomUUID();

            // Create unique request body for each user
            Map<String, Object> duplicateRequestBody = new HashMap<>();
            duplicateRequestBody.put("account_id", accountId);
            duplicateRequestBody.put("credit_card", cardNumber);
            duplicateRequestBody.put("amount", amount.setScale(2, RoundingMode.HALF_UP)); // Explicit scale
            duplicateRequestBody.put("session_id", sessionId);
            duplicateRequestBody.put("idempotency_key", explicitIdempotencyKey); // Add explicit key

            // Bundle the two requests together in one callable to ensure they run in order
            requests.add(() -> {
                List<RechargeResult> results = new ArrayList<>();

                // First request - should succeed
                try {
                    Wallet walletBefore = getOrCreateWallet(accountId, sessionId);
                    BigDecimal initialBalance = walletBefore != null ? walletBefore.getFunds() : BigDecimal.ZERO;

                    ResponseEntity<?> response = performRechargeWithMap(duplicateRequestBody);
                    assertEquals(202, response.getStatusCode().value(), "First request should be accepted");

                    // Track the expected recharge amount
                    expectedValidRecharges.compute(accountId, (k, v) -> (v == null) ? amount : v.add(amount));

                    // Wait for the balance to be updated before proceeding to the second request
                    boolean verified = waitForBalanceUpdate(accountId, sessionId, initialBalance, amount);
                    results.add(new RechargeResult(
                            accountId,
                            amount,
                            true,
                            verified ? "First request succeeded" : "Balance not updated",
                            verified
                    ));

                    // Now we can be sure the first request was processed before proceeding

                    // Second request - should be idempotent
                    try {
                        ResponseEntity<?> duplicateResponse = performRechargeWithMap(duplicateRequestBody);

                        // If we get here, it was accepted with 202
                        assertEquals(202, duplicateResponse.getStatusCode().value(), "Duplicate request should be accepted");
                        // Don't track this in expected amounts - it's a duplicate so shouldn't affect balance
                        results.add(new RechargeResult(accountId, amount, true, "Duplicate accepted", true));
                    } catch (HttpClientErrorException.UnprocessableEntity ex) {
                        // This is the expected 422 case for explicitly rejecting duplicates
                        logger.info("Duplicate properly rejected with 422 for account {}", accountId);
                        results.add(new RechargeResult(accountId, amount, false, "Duplicate detected (expected)", true));
                    }

                } catch (Exception e) {
                    results.add(new RechargeResult(accountId, amount, false, e.getMessage(), false));
                    // If the first request fails, we don't attempt the second
                }

                return results;
            });
        }

        // 6. Mix up the requests for better concurrency testing
        Collections.shuffle(requests);

        // 7. Execute requests concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<List<RechargeResult>>> futures = executorService.invokeAll(requests);

        // 8. Wait for all requests to complete and collect results
        List<RechargeResult> results = new ArrayList<>();
        for (Future<List<RechargeResult>> future : futures) {
            try {
                results.addAll(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("Error waiting for request result: {}", e.getMessage());
            }
        }
        executorService.shutdown();

        // 9. Analyze results
        int successfulRequests = 0;
        int failedRequests = 0;
        int expectedFailures = 0;
        int unexpectedFailures = 0;

        for (RechargeResult result : results) {
            if (result.success()) {
                successfulRequests++;
            } else {
                failedRequests++;
                if (result.expectedOutcome()) {
                    expectedFailures++;
                } else {
                    unexpectedFailures++;
                }
            }
        }

        logger.info("Test Summary:");
        logger.info("- Total requests: {}", results.size());
        logger.info("- Successful requests: {}", successfulRequests);
        logger.info("- Failed requests: {}", failedRequests);
        logger.info("- Expected failures: {}", expectedFailures);
        logger.info("- Unexpected failures: {}", unexpectedFailures);

        // 10. Final verification of wallet balances
        for (String accountId : validAccountIds) {
            String sessionId = sessionIds.get(validAccountIds.indexOf(accountId));
            Wallet initialWallet = initialWallets.get(accountId);
            Wallet finalWallet = getOrCreateWallet(accountId, sessionId);

            BigDecimal initialBalance = initialWallet != null ? initialWallet.getFunds() : BigDecimal.ZERO;
            BigDecimal finalBalance = finalWallet != null ? finalWallet.getFunds() : BigDecimal.ZERO;
            BigDecimal expectedRecharges = expectedValidRecharges.getOrDefault(accountId, BigDecimal.ZERO);
            BigDecimal expectedBalance = initialBalance.add(expectedRecharges);

            logger.info("Account {}: Initial balance = {}, Final balance = {}, Expected balance = {}, Difference = {}",
                    accountId, initialBalance, finalBalance, expectedBalance, finalBalance.subtract(expectedBalance));

            // Verify balances after all operations
            assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0,
                    "Balance should be non-negative for account " + accountId);

            // The final balance should equal the initial balance plus all valid recharges
            assertEquals(0, finalBalance.compareTo(expectedBalance),
                    "Final balance should match expected balance for account " + accountId);

            // Verify small amounts were not applied
            if (smallAmountRequests.containsKey(accountId)) {
                // Small amount recharges should not have affected the balance
                BigDecimal smallAmountTotal = smallAmountRequests.get(accountId).stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                logger.info("Account {}: Total small amount recharges attempted: {}",
                        accountId, smallAmountTotal);

                // The final balance should not include small amounts
                assertNotEquals(
                        expectedBalance.add(smallAmountTotal).compareTo(finalBalance), 0,
                        "Small amount recharges should not be applied for account " + accountId
                );
            }
        }

        // Assert that we don't have any unexpected failures
        assertEquals(0, unexpectedFailures, "There should be no unexpected failures");
    }

    private Wallet getOrCreateWallet(String accountId, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("account_id", accountId);
        headers.set("session_id", sessionId);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Wallet> response = restTemplate.exchange(
                    BASE_URL + port + WALLET_API,
                    HttpMethod.GET,
                    entity,
                    Wallet.class
            );

            return response.getBody();
        } catch (Exception e) {
            logger.error("Error getting/creating wallet for {}: {}", accountId, e.getMessage());
            return null;
        }
    }

    private ResponseEntity<?> performRechargeWithMap(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        return restTemplate.exchange(
                BASE_URL + port + RECHARGE_API,
                HttpMethod.POST,
                entity,
                Object.class
        );
    }

    /**
     * Waits for a balance update to be reflected in the wallet.
     *
     * @param accountId      The account ID
     * @param sessionId      The session ID
     * @param initialBalance The initial balance before recharge
     * @param expectedAmount The amount that should be added
     * @return true if balance was updated correctly, false if timed out
     */
    private boolean waitForBalanceUpdate(String accountId, String sessionId,
                                         BigDecimal initialBalance, BigDecimal expectedAmount) {
        // The expected balance is AT LEAST initialBalance + expectedAmount
        // since other concurrent recharges may also have completed
        BigDecimal minimumExpectedBalance = initialBalance.add(expectedAmount);

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            try {
                // Wait before checking
                Thread.sleep(POLL_INTERVAL_MS);

                // Check current balance
                Wallet currentWallet = getOrCreateWallet(accountId, sessionId);
                if (currentWallet != null) {
                    BigDecimal currentBalance = currentWallet.getFunds();

                    // If balance is at least the expected minimum, consider it successful
                    if (currentBalance.compareTo(minimumExpectedBalance) >= 0) {
                        logger.info("Balance updated for account {} after {} attempts: {} (minimum expected: {})",
                                accountId, attempt + 1, currentBalance, minimumExpectedBalance);
                        return true;
                    }

                    logger.debug("Waiting for balance update for account {}: current={}, minimum expected={}",
                            accountId, currentBalance, minimumExpectedBalance);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for balance update");
                return false;
            } catch (Exception e) {
                logger.error("Error checking balance: {}", e.getMessage());
            }
        }

        logger.warn("Timed out waiting for balance update for account {}: minimum expected={}",
                accountId, minimumExpectedBalance);
        return false;
    }

    private record RechargeResult(String accountId, BigDecimal amount, boolean success, String message,
                                  boolean expectedOutcome) {
    }
}