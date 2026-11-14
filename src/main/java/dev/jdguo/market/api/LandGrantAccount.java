package dev.jdguo.market.api;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 土地出让账户. One sub-account inside 储蓄总池, not a third pool.
 * <p>
 * Only the server selling land, and later the disposal of recovered land,
 * credits this account. An ordinary player-to-player land trade does not.
 * The balance earns the same demand-deposit interest as a personal savings
 * balance. It is not the shop operating account and it is not issuance reserves.
 * Moving money here from a player only changes whose savings claim it is.
 * The 储蓄总池 total does not change.
 * <p>
 * {@code receive} debits the payer's savings and credits this account.
 * {@code refund} reverses that movement. Both must be saved before they return,
 * and a failure must leave every balance unchanged.
 */
public interface LandGrantAccount {
    SettlementResult receive(UUID payer, String currency, BigInteger amount, String reference);

    SettlementResult refund(UUID payer, String currency, BigInteger amount, String reference);

    /**
     * Current balance, or null when the account cannot be read.
     */
    BigInteger balance(String currency);

    static LandGrantAccount installed() {
        return Holder.account;
    }

    static void install(LandGrantAccount account) {
        Holder.account = account;
    }

    final class Holder {
        private static LandGrantAccount account;

        private Holder() {
        }
    }
}
