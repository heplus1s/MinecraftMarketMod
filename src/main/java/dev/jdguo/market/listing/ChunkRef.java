package dev.jdguo.market.listing;

/**
 * One vanilla chunk column. Coordinates are chunk coordinates, not block coordinates.
 */
public record ChunkRef(String dimension, int x, int z) {
    public ChunkRef {
        dimension = dimension == null ? "" : dimension;
    }

    public String position() {
        return x + " " + z;
    }
}
