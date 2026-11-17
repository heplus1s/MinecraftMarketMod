package dev.jdguo.market.listing;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.api.SettlementResult;

import net.minecraft.nbt.NbtCompound;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Pay the seller, or the land-grant account, and only then hand over the goods.
 * A failed handoff refunds. The listing stays in the save when a refund itself fails.
 */
public final class Checkout {
    private Checkout() {
    }

    public record Outcome(boolean ok, String code, Listing listing, BigInteger amount, boolean pendingDelivery) {
        public static Outcome fail(String code) {
            return new Outcome(false, code, null, BigInteger.ZERO, false);
        }
    }

    public static Outcome buy(MarketBook book, UUID listingId, UUID buyer, String buyerName, BigInteger amount,
                              Payments payments, Handoff handoff, Runnable persist) {
        Listing listing = book.get(listingId).orElse(null);
        if (listing == null) {
            return Outcome.fail("missing");
        }
        if (listing.status() != ListingStatus.OPEN) {
            return Outcome.fail("reserved");
        }
        if (buyer.equals(listing.seller())) {
            return Outcome.fail("self");
        }
        if (amount == null || amount.signum() <= 0) {
            return Outcome.fail("price");
        }
        if (listing.priceMode() == PriceMode.CUSTOM && !listing.customTotal().equals(amount)) {
            return Outcome.fail("price");
        }
        Deal deal;
        try {
            deal = book.reserve(listing.id(), buyer, buyerName, amount);
        } catch (RuntimeException exception) {
            return Outcome.fail("reserved");
        }
        persist.run();
        SettlementResult paid = charge(listing, buyer, amount, deal, payments);
        if (!paid.success()) {
            if ("bank_pending".equals(paid.code()) || "bank_failed".equals(paid.code())) {
                return Outcome.fail(paid.code());
            }
            book.abort(deal.id());
            persist.run();
            return Outcome.fail(paid.code());
        }
        book.markPaid(deal.id());
        persist.run();
        Handoff.Outcome handed;
        try {
            handed = handoff.deliver(listing, buyer, buyerName);
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.error("Market delivery failed for {}", listing.id(), exception);
            // A hook can throw after changing ownership. Do not refund an unknown outcome.
            return Outcome.fail("delivery_pending");
        }
        if (handed == null) {
            return Outcome.fail("delivery_pending");
        }
        if (!handed.ok()) {
            return undo(book, listing, buyer, amount, deal, payments, handed, persist);
        }
        NbtCompound pending = handed.pendingItem();
        boolean waiting = pending != null && !pending.isEmpty();
        // Flush external handoff state while the recovery journal is still PAID.
        persist.run();
        if (!finalizeDelivered(deal, payments)) return Outcome.fail("delivery_pending");
        book.complete(deal.id(), pending);
        persist.run();
        return new Outcome(true, "ok", listing, amount, waiting);
    }

    public static void recover(MarketBook book, Payments payments, Handoff handoff, Runnable persist) {
        recover(book, payments, handoff, persist, false);
    }

    /** Periodic retries are land-only: legacy inventory insertion has no durable delivery receipt. */
    public static void recover(MarketBook book, Payments payments, Handoff handoff, Runnable persist, boolean landOnly) {
        for (Deal deal : book.deals()) {
            Listing candidate = book.get(deal.listingId()).orElse(null);
            if (landOnly && (candidate == null || candidate.kind() == ListingKind.ITEM)) continue;
            SettlementResult refundReceipt = payments.receipt(deal.refundReference());
            if (refundReceipt != null && refundReceipt.success()) {
                book.abort(deal.id());
                persist.run();
                continue;
            }
            if (receiptUncertain(refundReceipt)) {
                continue;
            }
            if (deal.stage() == Deal.Stage.RESERVED) {
                SettlementResult debitReceipt = payments.receipt(deal.reference());
                if (receiptUncertain(debitReceipt)) {
                    continue;
                }
                if (!debitReceipt.success()) {
                    book.abort(deal.id());
                    persist.run();
                    continue;
                }
                book.markPaid(deal.id());
                persist.run();
            }
            Listing listing = book.get(deal.listingId()).orElse(null);
            if (listing == null) {
                SettlementResult refund = refundDeal(deal, payments);
                if (refund.success()) {
                    book.abort(deal.id());
                    persist.run();
                } else {
                    MarketMod.LOGGER.error("Paid market deal {} has no listing and could not be refunded ({})", deal.id(), refund.code());
                }
                continue;
            }
            // There is no durable exactly-once receipt for legacy inventory insertion. A PAID
            // item deal can already have delivered; never replay it or refund it automatically.
            if (listing.kind() == ListingKind.ITEM) {
                MarketMod.LOGGER.error("Paid item deal {} requires inventory reconciliation; not replaying", deal.id());
                continue;
            }
            Handoff.Outcome handed;
            try {
                handed = handoff.deliver(listing, deal.buyer(), deal.buyerName());
            } catch (RuntimeException exception) {
                MarketMod.LOGGER.error("Could not finish paid market deal {}", deal.id(), exception);
                continue;
            }
            if (handed != null && handed.ok()) {
                persist.run();
                if (!finalizeDelivered(deal, payments)) continue;
                book.complete(deal.id(), handed.pendingItem());
                persist.run();
                continue;
            }
            if (handed == null) {
                continue; // Unknown delivery outcome is not evidence for a refund.
            }
            SettlementResult refund = refundDeal(deal, payments);
            if (!refund.success()) {
                MarketMod.LOGGER.error("Paid market deal {} could not be refunded ({})", deal.id(), refund.code());
                continue;
            }
            if (handed != null && handed.dropListing()) {
                book.discard(deal.id());
            } else {
                book.abort(deal.id());
            }
            persist.run();
        }
    }

    private static boolean finalizeDelivered(Deal deal, Payments payments) {
        // Only personal-currency system payments reserve a recursive collateral chain.
        // Player transfers and direct currency grants need no finalizer (old bank API stays compatible).
        if (deal.proceeds() != Proceeds.LAND_GRANT || !payments.requiresFinalization(deal.currency())) return true;
        try {
            SettlementResult result = payments.finalizePayment(deal.reference());
            return result != null && result.success();
        } catch (RuntimeException failure) {
            MarketMod.LOGGER.error("Could not finalize delivered land payment {}", deal.id(), failure);
            return false;
        }
    }

    private static boolean receiptUncertain(SettlementResult receipt) {
        return receipt == null || (!receipt.success() && !"unrecorded".equals(receipt.code()));
    }

    private static Outcome undo(MarketBook book, Listing listing, UUID buyer, BigInteger amount, Deal deal,
                                Payments payments, Handoff.Outcome handed, Runnable persist) {
        SettlementResult refund = refund(listing, buyer, amount, deal, payments);
        if (!refund.success()) {
            persist.run();
            MarketMod.LOGGER.error("Market refund failed for listing {} ({})", listing.id(), refund.code());
            return Outcome.fail("refund_failed");
        }
        if (handed != null && handed.dropListing()) {
            book.discard(deal.id());
        } else {
            book.abort(deal.id());
        }
        persist.run();
        String code = handed == null || handed.code() == null || handed.code().isBlank() ? "delivery" : handed.code();
        return Outcome.fail(code);
    }

    private static SettlementResult charge(Listing listing, UUID buyer, BigInteger amount, Deal deal, Payments payments) {
        if (listing.proceeds() == Proceeds.LAND_GRANT) {
            return payments.payLandGrant(buyer, listing.currency(), amount, deal.reference());
        }
        return payments.paySeller(buyer, listing.seller(), listing.currency(), amount, deal.reference());
    }

    private static SettlementResult refund(Listing listing, UUID buyer, BigInteger amount, Deal deal, Payments payments) {
        if (listing.proceeds() == Proceeds.LAND_GRANT) {
            return payments.refundLandGrant(buyer, listing.currency(), amount, deal.refundReference());
        }
        return payments.refundSeller(listing.seller(), buyer, listing.currency(), amount, deal.refundReference());
    }

    private static SettlementResult refundDeal(Deal deal, Payments payments) {
        if (deal.proceeds() == Proceeds.LAND_GRANT) {
            return payments.refundLandGrant(deal.buyer(), deal.currency(), deal.amount(), deal.refundReference());
        }
        return payments.refundSeller(deal.payee(), deal.buyer(), deal.currency(), deal.amount(), deal.refundReference());
    }
}
