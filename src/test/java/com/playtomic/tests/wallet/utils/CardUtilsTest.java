package com.playtomic.tests.wallet.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CardUtilsTest {

    // Test cases for maskCardNumber()
    @ParameterizedTest
    @ValueSource(strings = {"1234", "1", ""})
    void maskCardNumber_shortOrEmptyInput_returnsSame(String input) {
        assertEquals(input, CardUtils.maskCardNumber(input));
    }

    @ParameterizedTest
    @MethodSource("provideCardNumbersForMasking")
    void maskCardNumber_validInputs_properlyMasked(String input, String expected) {
        assertEquals(expected, CardUtils.maskCardNumber(input));
    }

    private static Stream<Object[]> provideCardNumbersForMasking() {
        return Stream.of(
                new Object[]{"1234567890123456", "************3456"},
                new Object[]{"4111111111111111", "************1111"},
                new Object[]{"378282246310005", "***********0005"},
                new Object[]{"6011111111111117", "************1117"},
                new Object[]{"5555555555554444", "************4444"}
        );
    }

    @Test
    void maskCardNumber_rejectsUnicode() {
        assertThrows(IllegalArgumentException.class,
                () -> CardUtils.maskCardNumber("💳1234"));
    }

    @Test
    void maskCardNumber_handlesAscii() {
        assertEquals("************3456",
                CardUtils.maskCardNumber("4111111111113456"));
        assertEquals("1234",
                CardUtils.maskCardNumber("1234"));
    }

    // Test cases for bytesToHex()
    @Test
    void bytesToHex_emptyArray_returnsEmptyString() {
        assertEquals("", CardUtils.bytesToHex(new byte[]{}));
    }

    @Test
    void bytesToHex_nullInput_throwsException() {
        assertThrows(NullPointerException.class, () -> CardUtils.bytesToHex(null));
    }

    @ParameterizedTest
    @MethodSource("provideBytesForHexConversion")
    void bytesToHex_variousInputs_correctConversion(byte[] input, String expected) {
        assertEquals(expected, CardUtils.bytesToHex(input));
    }

    private static Stream<Object[]> provideBytesForHexConversion() {
        return Stream.of(
                new Object[]{new byte[]{0}, "00"},
                new Object[]{new byte[]{127}, "7f"},
                new Object[]{new byte[]{-128}, "80"},
                new Object[]{new byte[]{0, 1, 2, 3, 4}, "0001020304"},
                new Object[]{new byte[]{-1, -2, -3, -4}, "fffefdfc"},
                new Object[]{"Hello".getBytes(StandardCharsets.UTF_8), "48656c6c6f"},
                new Object[]{new byte[]{0x12, 0x34, 0x56, 0x78}, "12345678"}
        );
    }

    @Test
    void bytesToHex_singleByteValues_properPadding() {
        // Test all single byte values (0-255)
        for (int i = 0; i <= 255; i++) {
            byte b = (byte) i;
            String hex = CardUtils.bytesToHex(new byte[]{b});
            assertEquals(String.format("%02x", i), hex);
        }
    }
}