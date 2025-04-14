package com.playtomic.tests.wallet.api;

import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.service.WalletService;
import jakarta.transaction.RollbackException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }


    @PostMapping("/recharge")
    public ResponseEntity<?> depositFunds(@Valid @RequestBody PaymentRequest paymentRequest) throws WalletNotFoundException, TransactionNotFoundException, RollbackException {
        walletService.depositFundsToAccount(paymentRequest);
        return ResponseEntity.ok(HttpStatus.ACCEPTED);
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Object> handleWalletNotFoundException(WalletNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @GetMapping("/")
    public ResponseEntity<?> getWallet(@RequestHeader("account_id") String accountId,
                                       @RequestHeader("session_id") String sessionId) {
        var wallet = walletService.getOrCreateWalletByAccountId(accountId, sessionId);
        if (wallet.isPresent()) {
            // todo: create wallet dto for better api response
            return ResponseEntity.ok(wallet);
        }
        return ResponseEntity.internalServerError().build();
    }
}