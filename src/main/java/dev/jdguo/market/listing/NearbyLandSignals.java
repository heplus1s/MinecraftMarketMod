package dev.jdguo.market.listing;

import java.util.*;

/** Request-local spatial index. Each seller contributes their maximum weight, never one weight per chunk. */
public final class NearbyLandSignals {
    public record Point(ChunkRef chunk, UUID seller, long weight) {}
    private record Cell(String dimension, long x, long z) {}
    private final Map<Cell, List<Point>> buckets = new HashMap<>();
    private final int radius;
    private final long side;
    public NearbyLandSignals(Collection<Point> points, int radius) {
        if (radius < 0 || radius > 128) throw new IllegalArgumentException("radius");
        this.radius=radius; this.side=radius+1L;
        for (Point point : points) {
            if (point.weight() < 0 || point.weight() > 10000) throw new IllegalArgumentException("weight");
            if (point.weight() == 0) continue;
            ChunkRef c=point.chunk();
            buckets.computeIfAbsent(new Cell(c.dimension(),Math.floorDiv((long)c.x(),side),Math.floorDiv((long)c.z(),side)), ignored->new ArrayList<>()).add(point);
        }
    }
    public boolean any(ChunkRef chunk) {
        long x=Math.floorDiv((long)chunk.x(),side),z=Math.floorDiv((long)chunk.z(),side);
        for(long bx=x-1;bx<=x+1;bx++)for(long bz=z-1;bz<=z+1;bz++)
            for(Point p:buckets.getOrDefault(new Cell(chunk.dimension(),bx,bz),List.of()))
                if(LandPriceMath.nearby(chunk,p.chunk(),radius))return true;
        return false;
    }
    public long weight(ChunkRef chunk) {
        Map<UUID,Long> weights=new HashMap<>();
        long x=Math.floorDiv((long)chunk.x(),side),z=Math.floorDiv((long)chunk.z(),side);
        for(long bx=x-1;bx<=x+1;bx++)for(long bz=z-1;bz<=z+1;bz++)
            for(Point p:buckets.getOrDefault(new Cell(chunk.dimension(),bx,bz),List.of()))
                if(LandPriceMath.nearby(chunk,p.chunk(),radius))weights.merge(p.seller(),p.weight(),Math::max);
        return weights.values().stream().mapToLong(Long::longValue).sum();
    }
}
