package dev.jdguo.market.listing;

import java.util.*;

/** A fixed 30-day per-chunk anti-wash cooldown, independently of visible price decay. */
public final class LandSaleSignals {
    public static final long RETENTION_MILLIS = 30L * 86400000;
    public record Signal(ChunkRef chunk, UUID seller, long time) {}
    private final Map<ChunkRef, Signal> values = new LinkedHashMap<>();
    public List<Signal> all() { return List.copyOf(values.values()); }
    public void record(List<ChunkRef> chunks, UUID seller, long now) {
        prune(now);
        if (seller == null || now < 0) throw new IllegalArgumentException("sale signal");
        for (ChunkRef chunk : chunks) values.putIfAbsent(chunk, new Signal(chunk, seller, now));
    }
    public void restore(ChunkRef chunk, UUID seller, long time, long now) {
        if (chunk.dimension().isBlank() || seller == null || time < 0 || time > now || now - time >= RETENTION_MILLIS) return;
        values.putIfAbsent(chunk, new Signal(chunk, seller, time));
    }
    private void prune(long now) { values.values().removeIf(s -> now - s.time() >= RETENTION_MILLIS); }
}
