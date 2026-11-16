package dev.jdguo.market.listing;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LandSelectionTest {
    private final ChunkRef a = new ChunkRef("minecraft:overworld", 1, 2);
    private final ChunkRef b = new ChunkRef("minecraft:overworld", 2, 2);
    @Test void emptySelectionMeansEntireParcel() {
        assertEquals(List.of(a,b), LandSelection.validate(List.of(a,b), List.of(), 10));
    }
    @Test void selectedChunksAreNotReplacedByPlayerPosition() {
        assertEquals(List.of(b), LandSelection.validate(List.of(a,b), List.of(b,b), 10));
    }
    @Test void changedOwnershipOrDimensionFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> LandSelection.validate(List.of(a), List.of(b), 10));
        assertThrows(IllegalArgumentException.class, () -> LandSelection.validate(List.of(a), List.of(new ChunkRef("minecraft:the_nether",1,2)), 10));
    }
    @Test void oversizedAndEmptyParcelsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LandSelection.validate(List.of(a,b), List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> LandSelection.validate(List.of(), List.of(), 10));
    }
}
