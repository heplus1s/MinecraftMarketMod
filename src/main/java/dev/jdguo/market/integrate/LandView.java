package dev.jdguo.market.integrate;

import dev.jdguo.market.listing.ChunkRef;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record LandView(
        UUID id,
        String name,
        UUID owner,
        String ownerName,
        String dimension,
        int chunkCount,
        List<ChunkRef> chunks
) {
    public LandView {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        name = name == null ? "" : name;
        ownerName = ownerName == null ? "" : ownerName;
        dimension = dimension == null ? "" : dimension;
    }

    public boolean same(List<ChunkRef> other) {
        return other != null && new HashSet<>(chunks).equals(new HashSet<>(other));
    }
}
