package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryRecoverySafetyTest {
    static Listing item() {
        NbtCompound stack = new NbtCompound(); stack.putString("id", "minecraft:diamond"); stack.putByte("Count", (byte) 1);
        return Listing.item(UUID.randomUUID(), UUID.randomUUID(), "Seller", Proceeds.SELLER,
                PriceMode.CUSTOM, BigInteger.ONE, "note", 1, "minecraft:diamond", stack);
    }
    @Test void interruptedDepositIsNotPurchasableOrPublicAfterRestart() {
        MarketBook book = new MarketBook(); Listing item = item(); book.add(item); book.lockItem(item.id());
        MarketBook disk = MarketBook.read(book.write());
        assertEquals(ListingStatus.RESERVED, disk.get(item.id()).orElseThrow().status());
        assertTrue(disk.search(null, null, null, false).isEmpty());
        assertThrows(IllegalStateException.class, () -> disk.reserve(item.id(), UUID.randomUUID(), "Buyer", BigInteger.ONE));
        assertTrue(disk.deals().isEmpty());
    }
    @Test void confirmedDepositCanOpenAndTransactionCannotBeUnlockedAsDeposit() {
        MarketBook book = new MarketBook(); Listing item = item(); book.add(item); book.lockItem(item.id());
        book.unlockItem(item.id());
        assertEquals(ListingStatus.OPEN, book.get(item.id()).orElseThrow().status());
        book.reserve(item.id(), UUID.randomUUID(), "Buyer", BigInteger.ONE);
        assertThrows(IllegalStateException.class, () -> book.unlockItem(item.id()));
    }
    @Test void mailboxLockPersistsAndKeepsOriginalGoodsForReconciliation() {
        MarketBook book = new MarketBook(); UUID buyer = UUID.randomUUID(); Listing item = item();
        book.addPending(buyer, item.item()); book.lockPendingDelivery(buyer);
        MarketBook disk = MarketBook.read(book.write());
        assertTrue(disk.pendingDeliveryLocked(buyer));
        assertEquals(1, disk.pendingOf(buyer).size());
        assertThrows(IllegalStateException.class, () -> disk.lockPendingDelivery(buyer));
        disk.replacePending(buyer, List.of()); disk.unlockPendingDelivery(buyer);
        assertFalse(MarketBook.read(disk.write()).pendingDeliveryLocked(buyer));
    }
    @Test void startupMustNotReplayPaidItemsOrRefundUnknownDelivery() {
        MarketBook book = new MarketBook(); Listing item = item(); book.add(item);
        Deal deal = book.reserve(item.id(), UUID.randomUUID(), "Buyer", BigInteger.ONE); book.markPaid(deal.id());
        AtomicInteger deliveries = new AtomicInteger();
        Payments payments = new NoWrites();
        MarketBook disk = MarketBook.read(book.write());
        Checkout.recover(disk, payments, (listing, buyer, name) -> {
            deliveries.incrementAndGet(); return Handoff.Outcome.success(null);
        }, () -> {});
        assertEquals(0, deliveries.get());
        assertEquals(Deal.Stage.PAID, disk.deals().get(0).stage());
        assertEquals(ListingStatus.RESERVED, disk.get(item.id()).orElseThrow().status());
    }
    private static class NoWrites implements Payments {
        public SettlementResult receipt(String r) { return SettlementResult.fail("unrecorded", ""); }
        public SettlementResult paySeller(UUID a, UUID b, String c, BigInteger n, String r) { throw new AssertionError(); }
        public SettlementResult payLandGrant(UUID a, String c, BigInteger n, String r) { throw new AssertionError(); }
        public SettlementResult refundSeller(UUID a, UUID b, String c, BigInteger n, String r) { throw new AssertionError(); }
        public SettlementResult refundLandGrant(UUID a, String c, BigInteger n, String r) { throw new AssertionError(); }
    }
}
