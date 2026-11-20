package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerLandResaleTest {
    private static final UUID BUYER = UUID.randomUUID();
    private static Listing listing() {
        String world = "minecraft:the_end";
        return Listing.land(UUID.randomUUID(), ListingKind.PARCEL, ServerLandIdentity.ID, ServerLandIdentity.LABEL,
                Proceeds.LAND_GRANT, BigInteger.TEN, "note", UUID.randomUUID(), "Land",
                world, List.of(new ChunkRef(world, 0, 0)));
    }

    @Test void regularBuyerPaysQuotedAmountIntoLandEscrowAndReleasesOnlyAfterTransfer() {
        var listing = listing();
        var book = new MarketBook();
        book.add(listing);
        var pay = new Recording();
        assertTrue(Checkout.buy(book, listing.id(), BUYER, "Player", BigInteger.TEN, pay, (l, b, n) -> {
            assertEquals(ServerLandIdentity.ID, l.seller());
            assertEquals(BUYER, b);
            assertEquals(List.of("escrow"), pay.events);
            pay.events.add("transfer");
            return Handoff.Outcome.success(null);
        }, () -> {}).ok());
        assertEquals(List.of("escrow", "transfer", "release"), pay.events);
        assertTrue(book.deals().isEmpty());
        assertTrue(book.get(listing.id()).isEmpty());
    }

    @Test void refusalRefundsLandEscrowNotSellerAndKeepsOwnership() {
        var listing = listing();
        var book = new MarketBook();
        book.add(listing);
        var pay = new Recording();
        assertFalse(Checkout.buy(book, listing.id(), BUYER, "Player", BigInteger.TEN, pay,
                (l, b, n) -> Handoff.Outcome.failure("gate"), () -> {}).ok());
        assertEquals(List.of("escrow", "refund"), pay.events);
        assertEquals(ListingStatus.OPEN, listing.status());
    }

    @Test void strictReleaseFailureLeavesPaidJournalAndRecoveryDoesNotDebitAgain() {
        var listing = listing();
        var book = new MarketBook();
        book.add(listing);
        var pay = new Recording();
        pay.allowComplete = false;
        assertEquals("delivery_pending", Checkout.buy(book, listing.id(), BUYER, "Player", BigInteger.TEN, pay,
                (l, b, n) -> Handoff.Outcome.success(null), () -> {}).code());
        MarketBook restored = MarketBook.read(book.write());
        assertEquals(Deal.Stage.PAID, restored.deals().get(0).stage());
        pay.allowComplete = true;
        Checkout.recover(restored, pay, (l, b, n) -> Handoff.Outcome.success(null), () -> {}, true);
        assertEquals(List.of("escrow", "release"), pay.events);
        assertTrue(restored.deals().isEmpty());
    }

    private static class Recording implements Payments {
        final List<String> events = new ArrayList<>();
        String charged;
        boolean allowComplete = true;
        public SettlementResult governmentReceipt(String r) {
            return r.equals(charged) ? SettlementResult.ok() : SettlementResult.fail("unrecorded", "");
        }
        public SettlementResult payGovernmentResale(UUID buyer, String c, BigInteger amount, String ref) {
            assertEquals(BUYER, buyer); assertEquals("note", c); assertEquals(BigInteger.TEN, amount);
            events.add("escrow"); charged = ref; return SettlementResult.ok();
        }
        public SettlementResult finalizeGovernmentResale(String ref) {
            assertEquals(charged, ref);
            if (!allowComplete) return SettlementResult.fail("missing", "");
            events.add("release"); return SettlementResult.ok();
        }
        public SettlementResult refundGovernmentResale(UUID b, String c, BigInteger n, String r) {
            assertEquals(BUYER, b); assertEquals("note", c); assertEquals(BigInteger.TEN, n);
            events.add("refund"); return SettlementResult.ok();
        }
        public SettlementResult refundLandGrant(UUID b, String c, BigInteger n, String r) { return fail("legacy refund"); }
        public SettlementResult payLandGrant(UUID b, String c, BigInteger n, String r) { return fail("legacy charge"); }
        public SettlementResult finalizePayment(String r) { return fail("legacy release"); }
        public SettlementResult paySeller(UUID a, UUID b, String c, BigInteger n, String r) { return fail("player charge"); }
        public SettlementResult refundSeller(UUID a, UUID b, String c, BigInteger n, String r) { return fail("player refund"); }
    }
}
