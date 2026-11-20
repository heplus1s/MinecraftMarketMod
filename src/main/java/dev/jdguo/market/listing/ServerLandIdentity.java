package dev.jdguo.market.listing;

import java.util.UUID;

/** Shared with Land's reserved system ownership; never used as a bank savings user. */
public final class ServerLandIdentity {
    public static final UUID ID = UUID.fromString("cbd4b6ae-6633-3ba2-982f-700dc5d4fdb8");
    public static final String LABEL = "服务器用地";
    private ServerLandIdentity() {}
    public static boolean isResale(Proceeds proceeds, UUID seller) {
        return proceeds == Proceeds.LAND_GRANT && ID.equals(seller);
    }
}
