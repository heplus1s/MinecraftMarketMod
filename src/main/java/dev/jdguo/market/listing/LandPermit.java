package dev.jdguo.market.listing;

import java.util.function.Supplier;

/**
 * Set only while this market is transferring land it has already been paid for.
 * Splitting a parcel has no reference argument, so the land gate reads this
 * instead of allowing every split.
 */
public final class LandPermit {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private LandPermit() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static <T> T call(String reference, Supplier<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(reference);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}