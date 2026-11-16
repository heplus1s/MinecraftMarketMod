package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;

import java.math.BigInteger;
import java.util.UUID;

/**
 * The four savings movements checkout can ask for. Player trades use the seller
 * methods. Server land sales use the land-grant methods.
 */
public interface Payments {
    default SettlementResult receipt(String reference) {
        return SettlementResult.fail("receipt_unavailable", "");
    }

    /** Release collateral reserved for a successfully delivered system purchase. Idempotent. */
    default SettlementResult finalizePayment(String reference) { return SettlementResult.ok(); }

    SettlementResult paySeller(UUID buyer, UUID seller, String currency, BigInteger amount, String reference);

    SettlementResult payLandGrant(UUID buyer, String currency, BigInteger amount, String reference);

    SettlementResult refundSeller(UUID seller, UUID buyer, String currency, BigInteger amount, String reference);

    SettlementResult refundLandGrant(UUID buyer, String currency, BigInteger amount, String reference);
}
