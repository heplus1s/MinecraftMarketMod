package dev.jdguo.market.listing;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LandPriceMathTest {
    private BigInteger p(long base, int n, int l, long s) { return LandPriceMath.price(BigInteger.valueOf(base), n, 500, l, 100, s, 300); }
    @Test void noSignalsReturnsBase() { assertEquals(BigInteger.valueOf(32), p(32,0,0,0)); }
    @Test void ownersIncreasePriceWithoutCeiling() { assertEquals(BigInteger.valueOf(640),p(32,380,0,0)); assertTrue(p(32,Integer.MAX_VALUE,0,0).compareTo(p(32,380,0,0))>0); }
    @Test void listingsAndCompletedSalesReducePriceSeparately() {
        assertTrue(p(10000,10,1,0).compareTo(p(10000,10,0,0))<0);
        assertTrue(p(10000,10,0,10000).compareTo(p(10000,10,0,0))<0);
        assertTrue(p(10000,10,1,10000).compareTo(p(10000,10,1,0))<0);
    }
    @Test void fractionalPricesRoundUpAndNeverGoZero() { assertEquals(BigInteger.valueOf(34), p(32,1,0,0)); assertEquals(BigInteger.ONE,p(1,0,Integer.MAX_VALUE,Long.MAX_VALUE)); }
    @Test void hugeBaseDoesNotOverflow() { BigInteger base=BigInteger.TEN.pow(100); assertTrue(LandPriceMath.price(base,Integer.MAX_VALUE,10000,0,0,0,0).compareTo(base)>0); }
    @Test void decayIsLinearAndExpires() { assertEquals(10000, LandPriceMath.weight(100,100,1000)); assertEquals(5000,LandPriceMath.weight(100,600,1000)); assertEquals(0,LandPriceMath.weight(100,1100,1000)); }
    @Test void distancesDoNotOverflowOrCrossDimensions() { assertFalse(LandPriceMath.nearby(new ChunkRef("a",Integer.MIN_VALUE,0),new ChunkRef("a",Integer.MAX_VALUE,0),128)); assertFalse(LandPriceMath.nearby(new ChunkRef("a",0,0),new ChunkRef("b",0,0),128)); assertTrue(LandPriceMath.nearby(new ChunkRef("a",-8,8),new ChunkRef("a",0,0),8)); }
    @Test void quantityAndOwnerFactorsAreIndependent() {
        BigInteger base = BigInteger.valueOf(10000);
        assertEquals(BigInteger.valueOf(10500), LandPriceMath.price(base,1,500,72,8,0,0,0,0,0));
        assertEquals(BigInteger.valueOf(11000), LandPriceMath.price(base,1,500,9,8,500,0,0,0,0));
        assertEquals(BigInteger.valueOf(14500), LandPriceMath.price(base,1,500,72,8,500,0,0,0,0));
        assertEquals(BigInteger.valueOf(15000), LandPriceMath.price(base,2,500,72,8,500,0,0,0,0));
    }
    @Test void distanceKernelWeightsNearbyClaimsMoreThanDistantClaims() {
        var c = new ChunkRef("a", 0, 0);
        assertEquals(8, LandPriceMath.kernelUnits(c,new ChunkRef("a",1,1),8));
        assertEquals(1, LandPriceMath.kernelUnits(c,new ChunkRef("a",8,0),8));
        assertEquals(0, LandPriceMath.kernelUnits(c,new ChunkRef("a",9,0),8));
        assertEquals(0, LandPriceMath.kernelUnits(c,new ChunkRef("b",0,0),8));
        assertEquals(1, LandPriceMath.kernelUnits(c,c,0));
    }
    @Test void initialBatchReorderingCannotRemoveOrIncreaseItsDensityCost() {
        var a = new ChunkRef("a", 0, 0); var b = new ChunkRef("a", 1, 0); var c = new ChunkRef("a", 8, 0); var d = new ChunkRef("a", 20, 0);
        assertArrayEquals(new long[]{0,8,3,0}, LandPriceMath.batchDensityWeights(List.of(a,b,c,d),8));
        assertArrayEquals(new long[]{3,0,0,8}, LandPriceMath.batchDensityWeights(List.of(c,d,a,b),8));
        assertThrows(IllegalArgumentException.class, () -> LandPriceMath.batchDensityWeights(List.of(a,a),8));
    }
    @Test void densityAndDownwardSignalsShareExactRounding() {
        assertEquals(BigInteger.valueOf(110),LandPriceMath.price(BigInteger.valueOf(100),1,500,16,8,500,1,100,10000,300));
    }
    @Test void invalidInputsRejected() { assertThrows(IllegalArgumentException.class,()->p(0,0,0,0)); assertThrows(IllegalArgumentException.class,()->p(1,-1,0,0)); }
}
