package dev.jdguo.market.listing;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Integer basis-point arithmetic; no floating rounding and no artificial upper price cap. */
public final class LandPriceMath {
    private LandPriceMath() {}
    public static BigInteger price(BigInteger base, int owners, int ownerBps, int listings, int listingBps, long saleWeightBps, int saleBps) {
        return price(base, owners, ownerBps, 0, 0, 0, listings, listingBps, saleWeightBps, saleBps);
    }
    public static BigInteger price(BigInteger base, int owners, int ownerBps, long densityWeight, int radius, int densityBps,
                                   int listings, int listingBps, long saleWeightBps, int saleBps) {
        return price(base, 100, owners, ownerBps, densityWeight, radius, densityBps, listings, listingBps, saleWeightBps, saleBps);
    }
    public static BigInteger price(BigInteger base, int basePercent, int owners, int ownerBps, long densityWeight, int radius, int densityBps,
                                   int listings, int listingBps, long saleWeightBps, int saleBps) {
        if (basePercent < DimensionFirstSalePolicy.NORMAL || basePercent > DimensionFirstSalePolicy.MAX_PERCENT)
            throw new IllegalArgumentException("base percent");
        if (radius < 0 || radius > 128 || densityBps < 0 || densityWeight < 0)
            throw new IllegalArgumentException("density pricing");
        if (base == null || base.signum() <= 0 || owners < 0 || listings < 0 || saleWeightBps < 0
                || ownerBps < 0 || listingBps < 0 || saleBps < 0) throw new IllegalArgumentException("pricing");
        BigInteger ten = BigInteger.valueOf(10000);
        BigInteger scale = BigInteger.valueOf(radius + 1L);
        BigInteger demand = ten.add(BigInteger.valueOf(owners).multiply(BigInteger.valueOf(ownerBps))).multiply(scale)
                .add(BigInteger.valueOf(densityWeight).multiply(BigInteger.valueOf(densityBps)));
        BigInteger numerator = base.multiply(BigInteger.valueOf(basePercent)).multiply(demand).multiply(ten);
        BigInteger denominator = ten.add(BigInteger.valueOf(listings).multiply(BigInteger.valueOf(listingBps))).multiply(ten)
                .add(BigInteger.valueOf(saleWeightBps).multiply(BigInteger.valueOf(saleBps))).multiply(scale).multiply(BigInteger.valueOf(100));
        // One minimum indivisible pricing unit, never zero or negative.
        return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator);
    }
    public static long kernelUnits(ChunkRef a, ChunkRef b, int radius) {
        if (!nearby(a, b, radius)) return 0;
        return radius + 1L - Math.max(Math.abs((long)a.x()-b.x()), Math.abs((long)a.z()-b.z()));
    }

    /** Earlier parcels are priced as provisionally occupied only inside the same initial-sale packet. */
    public static long[] batchDensityWeights(List<ChunkRef> chunks, int radius) {
        if (radius < 0 || radius > 128 || chunks.size() > 1024 || new HashSet<>(chunks).size() != chunks.size())
            throw new IllegalArgumentException("batch pricing");
        var order = java.util.stream.IntStream.range(0, chunks.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> chunks.get(i).dimension())
                        .thenComparingInt(i -> chunks.get(i).x()).thenComparingInt(i -> chunks.get(i).z())).toList();
        long[] weights = new long[chunks.size()];
        for (int i = 0; i < order.size(); i++) {
            int target = order.get(i);
            for (int j = 0; j < i; j++) weights[target] += kernelUnits(chunks.get(target), chunks.get(order.get(j)), radius);
        }
        return weights;
    }
    public static boolean nearby(ChunkRef a, ChunkRef b, int radius) {
        return a.dimension().equals(b.dimension()) && Math.abs((long)a.x()-b.x()) <= radius && Math.abs((long)a.z()-b.z()) <= radius;
    }
    public static long weight(long timestamp, long now, long windowMillis) {
        long elapsed = Math.max(0, now - timestamp);
        return elapsed >= windowMillis ? 0 : (windowMillis - elapsed) * 10000 / windowMillis;
    }
}
