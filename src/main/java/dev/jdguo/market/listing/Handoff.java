package dev.jdguo.market.listing;

import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public interface Handoff {
    Outcome deliver(Listing listing, UUID buyer, String buyerName);

    record Outcome(boolean ok, String code, NbtCompound pendingItem, boolean dropListing) {
        public static Outcome success(NbtCompound pendingItem) {
            return new Outcome(true, "ok", pendingItem, false);
        }

        public static Outcome failure(String code) {
            return new Outcome(false, code, null, false);
        }

        public static Outcome drop(String code) {
            return new Outcome(false, code, null, true);
        }
    }
}
