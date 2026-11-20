package dev.jdguo.market.listing;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class DimensionFirstSalePolicyTest {
    private static ChunkRef chunk(String dimension, int x, int z) { return new ChunkRef(dimension, x, z); }

    @Test void defaultsUseExactDimensionKeysAndChunkCenters() {
        var p = DimensionFirstSalePolicy.DEFAULT;
        assertEquals(125, p.percent(chunk("minecraft:the_nether", 1000, -500)));
        assertEquals(100, p.percent(chunk("minecraft:overworld", 0, 0)));
        assertEquals(100, p.percent(chunk("custom:the_nether", 0, 0)));
        assertEquals(200, p.percent(chunk("minecraft:the_end", 15, 0)));
        assertEquals(200, p.percent(chunk("minecraft:the_end", -16, -1)));
        assertEquals(100, p.percent(chunk("minecraft:the_end", 16, 0)));
        assertEquals(100, p.percent(chunk("minecraft:the_end", 15, 15)));
        assertEquals(100, p.percent(chunk("minecraft:the_end", Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test void explicitSettingsChangePremiumAndRadiusZeroDisablesCenter() {
        var p = new DimensionFirstSalePolicy(150, 300, 12);
        assertEquals(300, p.percent(chunk("minecraft:the_end", 0, 0)));
        assertEquals(100, p.percent(chunk("minecraft:the_end", 1, 0)));
        assertEquals(150, p.percent(chunk("minecraft:the_nether", 0, 0)));
        assertEquals(100, new DimensionFirstSalePolicy(125, 200, 0).percent(chunk("minecraft:the_end", 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new DimensionFirstSalePolicy(99, 200, 256));
        assertThrows(IllegalArgumentException.class, () -> new DimensionFirstSalePolicy(125, 1001, 256));
        assertThrows(IllegalArgumentException.class, () -> new DimensionFirstSalePolicy(125, 200, -1));
        assertThrows(IllegalArgumentException.class, () -> new DimensionFirstSalePolicy(125, 200, 30000001));
    }

    @Test void firstSalePremiumWorksWithZeroSignalsAndRoundsOnlyOnceWithSignals() {
        BigInteger base = BigInteger.valueOf(32);
        assertEquals(BigInteger.valueOf(40), LandPriceMath.price(base, 125, 0, 500, 0, 8, 500, 0, 100, 0, 300));
        assertEquals(BigInteger.valueOf(64), LandPriceMath.price(base, 200, 0, 500, 0, 8, 500, 0, 100, 0, 300));
        // 3 × 125% ÷ 1.30 = 2.8846..., rounds to 3, not 4 from separately rounding the base.
        assertEquals(BigInteger.valueOf(3), LandPriceMath.price(BigInteger.valueOf(3), 125,
                0, 500, 0, 8, 500, 1, 3000, 0, 300));
    }
}
