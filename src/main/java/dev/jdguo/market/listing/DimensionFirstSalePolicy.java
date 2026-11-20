package dev.jdguo.market.listing;

/** System first-sale base premiums, independent of dynamic price signals. */
public record DimensionFirstSalePolicy(int netherPercent, int endCenterPercent, int endCenterRadius) {
    public static final int NORMAL = 100;
    public static final int MAX_PERCENT = 1000;
    public static final int MAX_RADIUS = 30000000;
    public static final DimensionFirstSalePolicy DEFAULT = new DimensionFirstSalePolicy(125, 200, 256);

    public DimensionFirstSalePolicy {
        if (netherPercent < NORMAL || netherPercent > MAX_PERCENT || endCenterPercent < NORMAL
                || endCenterPercent > MAX_PERCENT || endCenterRadius < 0 || endCenterRadius > MAX_RADIUS)
            throw new IllegalArgumentException("dimension first-sale settings");
    }

    public int percent(ChunkRef chunk) {
        if ("minecraft:the_nether".equals(chunk.dimension())) return netherPercent;
        if (!"minecraft:the_end".equals(chunk.dimension()) || endCenterRadius == 0) return NORMAL;
        long x = 16L * chunk.x() + 8L, z = 16L * chunk.z() + 8L;
        long radius = endCenterRadius;
        // Check bounds first so even a hostile int coordinate cannot overflow the square.
        return Math.abs(x) <= radius && Math.abs(z) <= radius && x * x + z * z <= radius * radius
                ? endCenterPercent : NORMAL;
    }
}
