package dev.jdguo.market.listing;

/**
 * Who receives the price. Player trades pay the seller.
 * Server land sales pay the land-grant account inside the savings pool.
 */
public enum Proceeds {
    SELLER,
    LAND_GRANT,
    GOVERNMENT_BUYBACK;

    public String token() {
        return switch (this) {
            case LAND_GRANT -> "land_grant";
            case GOVERNMENT_BUYBACK -> "government_buyback";
            case SELLER -> "seller";
        };
    }

    public static Proceeds parse(String token) {
        if ("land_grant".equals(token)) {
            return LAND_GRANT;
        }
        if ("government_buyback".equals(token)) return GOVERNMENT_BUYBACK;
        if ("seller".equals(token)) {
            return SELLER;
        }
        return null;
    }
}
