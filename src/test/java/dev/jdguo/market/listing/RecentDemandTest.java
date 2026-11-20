package dev.jdguo.market.listing;

import dev.jdguo.market.api.MarketItemPrices;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentDemandTest {
    private static final String ITEM = "minecraft:diamond";
    private static final String CURRENCY = "note";
    private static final long DAY = 86_400_000;

    @Test
    void excludesIndexedTaggedSystemUnfinishedAndSelfTradesAndReadsSavedHistory() {
        MarketBook book = new MarketBook();
        finish(book, 1, 2, 4);
        finish(book, 3, 4, 20);
        finish(book, 5, 6, 4);
        fill(book, 7, 8, 200, PriceMode.SHOP, Proceeds.SELLER, false, true);
        fill(book, 9, 10, 200, PriceMode.CUSTOM, Proceeds.SELLER, true, true);
        fill(book, 11, 12, 200, PriceMode.CUSTOM, Proceeds.LAND_GRANT, false, true);
        fill(book, 13, 14, 200, PriceMode.CUSTOM, Proceeds.SELLER, false, false);
        finish(book, 15, 15, 200);

        MarketBook restored = MarketBook.read(book.write());
        long now = System.currentTimeMillis();
        assertEquals(28L, restored.recentDemandUnits(ITEM, CURRENCY, now));
        assertEquals(new BigDecimal("3"), restored.recentItemUnitPrice(ITEM, CURRENCY, now));
        assertEquals(0L, restored.recentDemandUnits("minecraft:emerald", CURRENCY, now));
        assertEquals(0L, restored.recentDemandUnits(ITEM, "copper", now));
        assertEquals(0L, restored.recentDemandUnits(ITEM, CURRENCY, now + DAY + 1));
        assertNull(restored.recentItemUnitPrice(ITEM, CURRENCY, now + DAY + 1));
    }

    @Test
    void requiresVolumeAndDistinctBuyersSellersAndPairs() {
        MarketBook thin = new MarketBook();
        finish(thin, 1, 2, 5);
        finish(thin, 3, 4, 5);
        finish(thin, 5, 6, 5);
        assertEquals(0L, thin.recentDemandUnits(ITEM, CURRENCY, System.currentTimeMillis()));

        MarketBook concentrated = new MarketBook();
        finish(concentrated, 1, 2, 64);
        finish(concentrated, 1, 3, 64);
        finish(concentrated, 1, 4, 64);
        assertEquals(0L, concentrated.recentDemandUnits(ITEM, CURRENCY, System.currentTimeMillis()));
    }

    @Test
    void capsUnorderedPairsAndCapsEachParticipantAcrossBothRoles() {
        MarketBook pairOnly = new MarketBook();
        finish(pairOnly, 1, 2, 60);
        finish(pairOnly, 2, 1, 60);
        finish(pairOnly, 3, 4, 20);
        finish(pairOnly, 5, 6, 20);
        assertEquals(104L, pairOnly.recentDemandUnits(ITEM, CURRENCY, System.currentTimeMillis()));

        MarketBook book = new MarketBook();
        finish(book, 1, 2, 60);
        finish(book, 1, 8, 64);
        finish(book, 2, 1, 60);
        finish(book, 3, 4, 20);
        finish(book, 5, 6, 20);
        finish(book, 1, 7, 64);
        // The 64-unit cap on pair {1,2} is shared in both directions. Participant 1
        // contributes 128 across all pairs, plus the 40 units between other players.
        assertEquals(168L, book.recentDemandUnits(ITEM, CURRENCY, System.currentTimeMillis()));
        assertEquals(0L, MarketItemPrices.recentDemandUnits(ITEM, CURRENCY));
    }

    private static void finish(MarketBook book, int seller, int buyer, int count) {
        fill(book, seller, buyer, count, PriceMode.CUSTOM, Proceeds.SELLER, false, true);
    }

    private static void fill(MarketBook book, int seller, int buyer, int count, PriceMode mode, Proceeds proceeds,
                             boolean tagged, boolean complete) {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", ITEM);
        if (tagged) {
            NbtCompound metadata = new NbtCompound();
            metadata.putString("Custom", "not the standard item");
            stack.put("tag", metadata);
        }
        BigInteger unit = BigInteger.valueOf(mode == PriceMode.SHOP || tagged || proceeds != Proceeds.SELLER
                || seller == buyer || !complete ? 100 : 3);
        Listing listing = Listing.item(UUID.randomUUID(), new UUID(0, seller), "seller", proceeds, mode,
                unit, CURRENCY, count, ITEM, stack);
        assertTrue(book.add(listing));
        var deal = book.reserve(listing.id(), new UUID(0, buyer), "buyer", unit.multiply(BigInteger.valueOf(count)));
        book.markPaid(deal.id());
        if (complete) book.complete(deal.id(), null);
    }
}
