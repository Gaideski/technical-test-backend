package com.playtomic.tests.wallet.utils;

public class cardUtils {

    public static String maskCardNumber(String cardNumber) {
        // Keep only the last 4 digits of card number
        return cardNumber.length() <= 4
                ? cardNumber
                : "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
