package dev.jdguo.market.listing;

public enum PriceMode {
    CUSTOM,
    SHOP;

    public String token() {
        return this == SHOP ? "shop" : "custom";
    }

    public static PriceMode parse(String token) {
        if ("shop".equals(token)) {
            return SHOP;
        }
        if ("custom".equals(token)) {
            return CUSTOM;
        }
        return null;
    }
}
