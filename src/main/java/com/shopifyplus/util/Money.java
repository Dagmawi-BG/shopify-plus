package com.shopifyplus.util;

public final class Money {

    private Money() {
    }

    // Round to 2 decimals (cents), matching the Node round2 helper.
    public static double round2(double n) {
        return Math.round(n * 100.0) / 100.0;
    }
}
