package dev.jdguo.market.listing;
import java.util.List;
import java.util.LinkedHashSet;
/** Server-side target validation shared by opening and confirming a map listing. */
public final class LandSelection {
    private LandSelection() {}
    public static List<ChunkRef> validate(List<ChunkRef> owned, List<ChunkRef> requested, int max) {
        List<ChunkRef> selected = requested.isEmpty() ? List.copyOf(owned) : List.copyOf(new LinkedHashSet<>(requested));
        if (selected.isEmpty() || selected.size() > max || !owned.containsAll(selected))
            throw new IllegalArgumentException("stale_or_oversized_land_selection");
        return selected;
    }
}
