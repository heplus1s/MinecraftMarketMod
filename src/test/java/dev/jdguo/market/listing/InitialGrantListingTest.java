package dev.jdguo.market.listing;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialGrantListingTest {
    @Test void firstSaleRetainsMarkerScopeAndWholePriceAcrossSave() {
        var chunks = List.of(new ChunkRef("minecraft:overworld", 1, 1), new ChunkRef("minecraft:overworld", 1, 2));
        var listing = Listing.initialGrant(UUID.randomUUID(), BigInteger.valueOf(24), "personal:aaaaaaaa-0000-0000-0000-000000000001", "minecraft:overworld", chunks);
        Listing loaded = Listing.read(listing.write());
        assertNotNull(loaded);
        assertTrue(loaded.initialGrant());
        assertEquals(Proceeds.LAND_GRANT, loaded.proceeds());
        assertEquals(BigInteger.valueOf(24), loaded.customTotal());
        assertEquals(2, loaded.count());
        assertTrue(loaded.sameChunks(chunks));
        assertEquals(listing.currency(), loaded.currency());
    }
    @Test void firstSaleJournalIsNotAReusablePublicOffer() {
        var listing = Listing.initialGrant(UUID.randomUUID(), BigInteger.TEN, "note", "minecraft:overworld", List.of(new ChunkRef("minecraft:overworld", 0, 0)));
        MarketBook book = new MarketBook();
        assertTrue(book.add(listing));
        assertEquals(1, book.all().size());
        assertTrue(book.search(null, null, null, false).isEmpty());
        MarketBook loaded = MarketBook.read(book.write());
        assertTrue(loaded.get(listing.id()).orElseThrow().initialGrant());
        assertTrue(loaded.search(null, null, null, false).isEmpty());
    }
    @Test void canonicalPersonalCurrencyIdsAcceptedWithoutRelaxingArbitraryInput() {
        assertTrue(ListingTerms.validCurrencyId("note"));
        assertTrue(ListingTerms.validCurrencyId("personal:aaaaaaaa-0000-0000-0000-000000000001"));
        assertFalse(ListingTerms.validCurrencyId("personal:1"));
        assertFalse(ListingTerms.validCurrencyId("个人券")); // names must first be uniquely resolved
        assertFalse(ListingTerms.validCurrencyId("note other"));
    }
}
