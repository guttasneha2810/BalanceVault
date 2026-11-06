package com.ledgerline.util;

import java.util.UUID;

public final class TransactionReferenceGenerator {

    private TransactionReferenceGenerator() {
    }

    public static String generate(String prefix) {
        String cleanPrefix = (prefix != null && !prefix.isBlank()) ? prefix.toUpperCase() : "TXN";
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return cleanPrefix + "-" + uuid;
    }
}
