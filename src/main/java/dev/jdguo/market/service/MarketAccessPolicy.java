package dev.jdguo.market.service;

/** Client/request hints never replace current server permission. */
public final class MarketAccessPolicy {
    private MarketAccessPolicy() {}
    public static boolean canCancel(boolean owner, boolean requestedAdmin, boolean currentOperator) {
        return owner || (requestedAdmin && currentOperator);
    }
}
