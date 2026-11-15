package dev.jdguo.market.listing;

import java.util.UUID;

/**
 * Passed to {@code LandApi.transferParcel} and {@code transferChunk}.
 * The land mod uses the same {@code sale:} prefix for its own unpaid board.
 * A market purchase is this prefix plus the market listing id.
 */
public final class SaleReference {
    private SaleReference() {
    }

    public static String of(UUID listingId) {
        return "sale:" + listingId;
    }

    public static String payment(UUID dealId) {
        return "market-pay:" + dealId;
    }

    public static String refund(UUID dealId) {
        return "refund:" + dealId;
    }

    public static boolean matches(UUID listingId, String reference) {
        return reference != null && of(listingId).equals(reference);
    }
}
