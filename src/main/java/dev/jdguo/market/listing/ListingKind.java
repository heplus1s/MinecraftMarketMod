package dev.jdguo.market.listing;

public enum ListingKind {
    ITEM,
    PARCEL,
    CHUNKS;

    public String token() {
        return switch (this) {
            case ITEM -> "item";
            case PARCEL -> "parcel";
            case CHUNKS -> "chunks";
        };
    }

    public static ListingKind parse(String token) {
        if (token == null) {
            return null;
        }
        return switch (token) {
            case "item" -> ITEM;
            case "parcel" -> PARCEL;
            case "chunks" -> CHUNKS;
            default -> null;
        };
    }
}
