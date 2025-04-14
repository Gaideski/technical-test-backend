package com.playtomic.tests.wallet.model.exceptions;

import lombok.NonNull;

public class WalletNotFoundException extends Exception {
    public WalletNotFoundException(@NonNull String accountId) {
        super("Missing wallet for account: " + accountId);
    }
}
