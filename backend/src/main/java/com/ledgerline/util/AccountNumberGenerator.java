package com.ledgerline.util;

import java.security.SecureRandom;

public final class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    public static String generate() {
        // ACC followed by 10 digits
        long number = 1000000000L + (long)(RANDOM.nextDouble() * 9000000000L);
        return "ACC" + number;
    }
}
