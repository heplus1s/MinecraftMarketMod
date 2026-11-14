package dev.jdguo.market.listing;

/**
 * Who receives the price. Player trades pay the seller.
 * Server land sales pay the land-grant account inside the savings pool.
 */
public enum Proceeds {
    SELLER,
    LAND_GRANT;

    public String token() {
        return this == LAND_GRANT ? "land_grant" : "seller";
    }

    public static Proceeds parse(String token) {
        if ("land_grant".equals(token)) {
            return LAND_GRANT;
        }
        if ("seller".equals(token)) {
            return SELLER;
        }
        return null;
    }
}
