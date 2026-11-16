package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandRecoveryFinalizationTest {
    private static final UUID BUYER = UUID.randomUUID();
    private static final String PERSONAL = "personal:aaaaaaaa-0000-0000-0000-000000000001";
    private static Listing land() {
        return Listing.initialGrant(UUID.randomUUID(), BigInteger.TEN, PERSONAL, "minecraft:overworld", List.of(new ChunkRef("minecraft:overworld", 0, 0)));
    }
    @Test void appliedLandWithUnavailableFinalizerStaysPaidAndRetriesWithoutRepayment() {
        MarketBook book = new MarketBook(); Listing order = land(); book.add(order);
        FakePayments pay = new FakePayments();
        Set<UUID> granted = new HashSet<>(); AtomicInteger actualGrants = new AtomicInteger();
        Handoff grant = (listing, buyer, name) -> {
            if (granted.add(listing.id())) actualGrants.incrementAndGet();
            return Handoff.Outcome.success(null);
        };
        var result = Checkout.buy(book, order.id(), BUYER, "Buyer", BigInteger.TEN, pay, grant, () -> {});
        assertEquals("delivery_pending", result.code());
        assertEquals(Deal.Stage.PAID, book.deals().get(0).stage());
        MarketBook restored = MarketBook.read(book.write());
        Checkout.recover(restored, pay, grant, () -> {}, true);
        assertEquals(1, restored.deals().size());
        pay.finalizeOk = true;
        Checkout.recover(restored, pay, grant, () -> {}, true);
        assertTrue(restored.deals().isEmpty());
        assertEquals(1, pay.pays);
        assertEquals(1, actualGrants.get());
        assertEquals(0, pay.refunds);
    }
    @Test void uncertainLandNeverFinalizesOrRefundsAndDefiniteRejectionRefunds() {
        MarketBook book = new MarketBook(); Listing order = land(); book.add(order); FakePayments pay = new FakePayments();
        var result = Checkout.buy(book, order.id(), BUYER, "Buyer", BigInteger.TEN, pay,
                (listing, buyer, name) -> { throw new IllegalStateException("pending"); }, () -> {});
        assertEquals("delivery_pending", result.code());
        assertEquals(0, pay.finalizes); assertEquals(0, pay.refunds);
        Checkout.recover(book, pay, (listing, buyer, name) -> Handoff.Outcome.drop("stale"), () -> {}, true);
        assertTrue(book.deals().isEmpty()); assertEquals(1, pay.refunds); assertEquals(0, pay.finalizes);
    }
    @Test void periodicLandRecoveryDoesNotRepeatLegacyItemInsertion() {
        MarketBook book = new MarketBook();
        Listing item = Listing.item(UUID.randomUUID(), UUID.randomUUID(), "Seller", Proceeds.SELLER, PriceMode.CUSTOM,
                BigInteger.ONE, "note", 1, "minecraft:stone", new NbtCompound());
        book.add(item); Deal deal = book.reserve(item.id(), BUYER, "Buyer", BigInteger.ONE); book.markPaid(deal.id());
        FakePayments pay = new FakePayments(); AtomicInteger deliveries = new AtomicInteger();
        Checkout.recover(book, pay, (listing, buyer, name) -> { deliveries.incrementAndGet(); return Handoff.Outcome.success(null); }, () -> {}, true);
        assertEquals(0, deliveries.get()); assertEquals(1, book.deals().size());
    }
    private static final class FakePayments implements Payments {
        final Set<String> receipts = new HashSet<>();
        int pays, refunds, finalizes; boolean finalizeOk;
        public SettlementResult receipt(String ref) { return receipts.contains(ref) ? SettlementResult.ok() : SettlementResult.fail("unrecorded", ""); }
        public SettlementResult finalizePayment(String ref) { finalizes++; return finalizeOk ? SettlementResult.ok() : SettlementResult.fail("save_failed", ""); }
        public SettlementResult paySeller(UUID a, UUID b, String c, BigInteger n, String r) { throw new AssertionError("unexpected seller payment"); }
        public SettlementResult refundSeller(UUID a, UUID b, String c, BigInteger n, String r) { throw new AssertionError("unexpected seller refund"); }
        public SettlementResult payLandGrant(UUID a, String c, BigInteger n, String r) { pays++; receipts.add(r); return SettlementResult.ok(); }
        public SettlementResult refundLandGrant(UUID a, String c, BigInteger n, String r) { refunds++; receipts.add(r); return SettlementResult.ok(); }
    }
}
