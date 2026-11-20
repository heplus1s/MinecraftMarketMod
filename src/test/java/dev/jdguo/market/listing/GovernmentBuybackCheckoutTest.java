package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernmentBuybackCheckoutTest {
    private static final UUID SYSTEM = UUID.randomUUID(), SELLER = UUID.randomUUID(), ADMIN = UUID.randomUUID();
    private static final String WORLD = "minecraft:overworld";
    private static Listing land(String currency) {
        return Listing.land(UUID.randomUUID(), ListingKind.PARCEL, SELLER, "Seller", Proceeds.SELLER,
                BigInteger.TEN, currency, UUID.randomUUID(), "Land", WORLD, List.of(new ChunkRef(WORLD, 1, 1)));
    }

    private static Checkout.Outcome run(MarketBook book, Listing listing, Ledger payment, Handoff handoff) {
        return Checkout.buyGovernment(book, listing.id(), SYSTEM, "服务器用地", BigInteger.TEN, ADMIN, payment, handoff, () -> {});
    }

    @Test void holdsThenDeliversThenCreditsSellerAndRecordsAdmin() {
        var book = new MarketBook(); var listing = land("note"); book.add(listing);
        var ledger = new Ledger();
        assertTrue(run(book, listing, ledger, (l, buyer, name) -> {
            assertEquals(SYSTEM, buyer); assertEquals("服务器用地", name);
            assertEquals(List.of("hold"), ledger.events);
            ledger.events.add("deliver"); return Handoff.Outcome.success(null);
        }).ok());
        assertEquals(List.of("hold", "deliver", "payout"), ledger.events);
        var trade = book.recentTrades().get(0);
        assertEquals(ADMIN, trade.getUuid("Operator"));
        assertEquals(SYSTEM, trade.getUuid("Buyer"));
        assertEquals("government_buyback", trade.getString("Proceeds"));
    }

    @Test void deniedDeliveryOnlyReleasesSystemEscrow() {
        var book = new MarketBook(); var listing = land("note"); book.add(listing);
        var ledger = new Ledger();
        assertFalse(run(book, listing, ledger, (l,b,n) -> Handoff.Outcome.failure("gate")).ok());
        assertEquals(List.of("hold", "release"), ledger.events);
        assertTrue(book.deals().isEmpty());
        assertEquals(ListingStatus.OPEN, listing.status());
    }

    @Test void uncertainDeliveryWithholdsCreditAndRecoveryUsesSameJournal() {
        var book = new MarketBook(); var listing = land("note"); book.add(listing);
        var ledger = new Ledger();
        assertEquals("delivery_pending", run(book, listing, ledger, (l,b,n) -> null).code());
        MarketBook restored = MarketBook.read(book.write());
        var deal = restored.deals().get(0);
        assertEquals(ADMIN, deal.operator());
        assertEquals(Proceeds.GOVERNMENT_BUYBACK, deal.proceeds());
        assertEquals(Deal.Stage.PAID, deal.stage());
        assertEquals(List.of("hold"), ledger.events);
        Checkout.recover(restored, ledger, (l,b,n) -> Handoff.Outcome.success(null), () -> {}, true);
        Checkout.recover(restored, ledger, (l,b,n) -> fail("Already recovered"), () -> {}, true);
        assertTrue(restored.deals().isEmpty());
        assertEquals(List.of("hold", "payout"), ledger.events);
    }

    @Test void insufficientFundingLeavesListingOpenAndNoDelivery() {
        var book = new MarketBook(); var listing = land("note"); book.add(listing);
        var ledger = new Ledger(); ledger.canHold = false;
        assertEquals("insufficient_system", run(book, listing, ledger, (l,b,n) -> fail("Unfunded sale")).code());
        assertTrue(book.deals().isEmpty());
        assertEquals(ListingStatus.OPEN, listing.status());
        assertTrue(ledger.events.isEmpty());
    }

    @Test void nonNoteListingIsRejectedBeforePayment() {
        var book = new MarketBook(); var listing = land("copper"); book.add(listing);
        var ledger = new Ledger();
        assertFalse(run(book, listing, ledger, (l,b,n) -> fail("Invalid sale")).ok());
        assertTrue(ledger.events.isEmpty());
    }

    private static class Ledger implements Payments {
        final List<String> events = new ArrayList<>();
        boolean canHold = true;
        String held;
        public SettlementResult prepareGovernmentBuyback(UUID seller, String c, BigInteger a, String ref) {
            assertEquals(SELLER, seller); assertEquals("note", c); assertEquals(BigInteger.TEN, a);
            if (!canHold) return SettlementResult.fail("insufficient_system", "");
            held = ref; events.add("hold"); return SettlementResult.ok();
        }
        public SettlementResult finalizeGovernmentBuyback(String ref) {
            assertEquals(held, ref); events.add("payout"); return SettlementResult.ok();
        }
        public SettlementResult refundGovernmentBuyback(UUID seller, String c, BigInteger a, String ref, String refund) {
            assertEquals(SELLER, seller); assertEquals(held, ref); assertTrue(refund.startsWith("refund:"));
            events.add("release"); return SettlementResult.ok();
        }
        public SettlementResult governmentReceipt(String ref) {
            return Objects.equals(held, ref) ? SettlementResult.ok() : SettlementResult.fail("unrecorded", "");
        }
        public SettlementResult paySeller(UUID a, UUID b, String c, BigInteger n, String r) { return fail("Personal payment"); }
        public SettlementResult refundSeller(UUID a, UUID b, String c, BigInteger n, String r) { return fail("Personal refund"); }
        public SettlementResult payLandGrant(UUID a, String c, BigInteger n, String r) { return fail("Initial sale"); }
        public SettlementResult refundLandGrant(UUID a, String c, BigInteger n, String r) { return fail("Initial sale refund"); }
    }
}
