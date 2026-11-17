package dev.jdguo.market.listing;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartialListingTest {
    private Listing item(int count) {
        NbtCompound template = new NbtCompound();
        template.putString("id", "minecraft:stone"); template.putByte("Count", (byte)1);
        NbtCompound data = new NbtCompound(); data.putString("custom", "preserve-me"); template.put("tag", data);
        return Listing.item(UUID.randomUUID(), UUID.randomUUID(), "seller", Proceeds.SELLER,
                PriceMode.CUSTOM, BigInteger.valueOf(7), "note", count, "minecraft:stone", template);
    }
    @Test void splitAndReloadConserveQuantityPriceAndMetadata() {
        MarketBook book = new MarketBook(); Listing original = item(2304); book.add(original);
        Listing part = book.splitItem(original.id(), 65);
        assertEquals(2239, original.count()); assertEquals(65, part.count());
        assertEquals(BigInteger.valueOf(455), part.customTotal());
        assertEquals(original.seller(), part.seller()); assertEquals(original.createdAt(), part.createdAt());
        MarketBook loaded = MarketBook.read(book.write());
        assertEquals(2304, loaded.all().stream().mapToInt(Listing::count).sum());
        for (Listing listing : loaded.all()) {
            assertEquals(listing.count(), listing.item().getInt("MarketCount"));
            assertEquals("preserve-me", listing.item().getCompound("tag").getString("custom"));
        }
    }
    @Test void malformedOrReservedSplitNeverChangesEscrow() {
        MarketBook book = new MarketBook(); Listing original = item(64); book.add(original);
        for (int n : new int[]{-1, 0, 64, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> book.splitItem(original.id(), n));
            assertEquals(64, original.count()); assertEquals(1, book.size());
        }
        book.lockItem(original.id());
        assertThrows(IllegalArgumentException.class, () -> book.splitItem(original.id(), 1));
        assertEquals(64, original.count()); assertEquals(1, book.size());
    }
    @Test void completedPartLeavesParentAndExactMailboxQuantity() {
        MarketBook book = new MarketBook(); Listing original = item(200); book.add(original);
        Listing part = book.splitItem(original.id(), 129);
        Deal deal = book.reserve(part.id(), UUID.randomUUID(), "buyer", part.customTotal());
        book.markPaid(deal.id()); book.complete(deal.id(), part.item());
        MarketBook disk = MarketBook.read(book.write());
        assertEquals(71, disk.get(original.id()).orElseThrow().count());
        assertTrue(disk.get(part.id()).isEmpty());
        assertEquals(129, disk.pendingOf(deal.buyer()).get(0).item().getInt("MarketCount"));
        assertEquals(1, disk.tradeCount());
    }
    @Test void abortedPartRetainsBothOffersWithoutMintingEscrow() {
        MarketBook book = new MarketBook(); Listing original = item(200); book.add(original);
        Listing part = book.splitItem(original.id(), 129);
        Deal deal = book.reserve(part.id(), UUID.randomUUID(), "buyer", part.customTotal()); book.abort(deal.id());
        MarketBook disk = MarketBook.read(book.write());
        assertEquals(200, disk.all().stream().mapToInt(Listing::count).sum());
        assertTrue(disk.all().stream().allMatch(l -> l.status() == ListingStatus.OPEN));
        assertTrue(disk.deals().isEmpty());
    }
}
