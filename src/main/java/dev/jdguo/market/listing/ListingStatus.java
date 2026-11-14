package dev.jdguo.market.listing;

public enum ListingStatus {
    OPEN,
    RESERVED;

    public String token() {
        return this == RESERVED ? "reserved" : "open";
    }

    public static ListingStatus parse(String token) {
        if ("reserved".equals(token)) {
            return RESERVED;
        }
        if ("open".equals(token) || token == null || token.isEmpty()) {
            return OPEN;
        }
        return null;
    }
}
