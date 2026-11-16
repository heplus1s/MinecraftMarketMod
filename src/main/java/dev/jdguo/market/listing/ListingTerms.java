package dev.jdguo.market.listing;

import java.math.BigInteger;

/** Input validation shared by interactive listing and price-confirmation paths. */
public final class ListingTerms {
    private ListingTerms() {}
    public static boolean validCurrencyId(String id) {
        return id != null && id.matches("[a-z0-9_]{1,32}");
    }
    public static boolean withinMaximum(BigInteger amount, BigInteger maximum) {
        return amount != null && amount.signum() > 0 && (maximum == null ||
                maximum.signum() > 0 && amount.compareTo(maximum) <= 0);
    }
}
