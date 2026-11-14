package dev.jdguo.market.api;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Player-to-player savings transfer. The price is not income of the central bank.
 * <p>
 * A correct implementation debits the buyer's savings and credits the seller's
 * savings in the same currency. The 储蓄总池 total does not change. Issuance
 * reserves do not move. The shop operating account does not receive the price.
 * Paper currency in inventories is not involved. The ledger must be saved
 * before this call returns. A failed call must leave both balances unchanged.
 * <p>
 * The market also accepts {@link dev.jdguo.market.api.BankHookSpec#BANK_CLASS}
 * when the bank does not depend on this mod.
 */
public interface BankSettlement {
    SettlementResult transfer(UUID from, UUID to, String currency, BigInteger amount, String reference);

    static BankSettlement installed() {
        return Holder.settlement;
    }

    static void install(BankSettlement settlement) {
        Holder.settlement = settlement;
    }

    final class Holder {
        private static BankSettlement settlement;

        private Holder() {
        }
    }
}
