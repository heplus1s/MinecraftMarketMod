package dev.jdguo.market.listing;

import java.math.BigInteger;

/** Integer basis-point arithmetic; no floating rounding and no artificial upper price cap. */
public final class LandPriceMath {
    private LandPriceMath() {}
    public static BigInteger price(BigInteger base, int owners, int ownerBps, int listings, int listingBps, long saleWeightBps, int saleBps) {
        if (base == null || base.signum() <= 0 || owners < 0 || listings < 0 || saleWeightBps < 0
                || ownerBps < 0 || listingBps < 0 || saleBps < 0) throw new IllegalArgumentException("pricing");
        BigInteger ten = BigInteger.valueOf(10000);
        BigInteger numerator = base.multiply(ten.add(BigInteger.valueOf(owners).multiply(BigInteger.valueOf(ownerBps)))).multiply(ten);
        BigInteger denominator = ten.add(BigInteger.valueOf(listings).multiply(BigInteger.valueOf(listingBps))).multiply(ten)
                .add(BigInteger.valueOf(saleWeightBps).multiply(BigInteger.valueOf(saleBps)));
        // One minimum indivisible pricing unit, never zero or negative.
        return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator);
    }
    public static boolean nearby(ChunkRef a, ChunkRef b, int radius) {
        return a.dimension().equals(b.dimension()) && Math.abs((long)a.x()-b.x()) <= radius && Math.abs((long)a.z()-b.z()) <= radius;
    }
    public static long weight(long timestamp, long now, long windowMillis) {
        long elapsed = Math.max(0, now - timestamp);
        return elapsed >= windowMillis ? 0 : (windowMillis - elapsed) * 10000 / windowMillis;
    }
}
