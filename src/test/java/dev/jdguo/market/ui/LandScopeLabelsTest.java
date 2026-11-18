package dev.jdguo.market.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandScopeLabelsTest {
    @Test void parcelsRemainWholeIncludingSingleChunkParcels() {
        assertEquals("整宗（1区块）", LandScopeLabels.label("parcel", 1));
        assertEquals("整宗（17区块）", LandScopeLabels.label("PARCEL", 17));
    }
    @Test void selectedChunksNeverClaimWholeParcel() {
        assertEquals("单区块（1区块）", LandScopeLabels.label("chunks", 1));
        assertEquals("选区（17区块）", LandScopeLabels.label("CHUNKS", 17));
    }
    @Test void unknownKindsNeverGuessWholeParcel() {
        assertEquals("领地选区（9区块）", LandScopeLabels.label(null, 9));
        assertEquals("领地选区（9区块）", LandScopeLabels.label("", 9));
        assertEquals("领地选区（9区块）", LandScopeLabels.label("new_server_kind", 9));
    }
    @Test void unavailableCountsNeverInventScope() {
        assertEquals("领地选区（范围待同步）", LandScopeLabels.label("parcel", 0));
        assertEquals("领地选区（范围待同步）", LandScopeLabels.label("chunks", -1));
    }
    @Test void fullServerCountIsShownRatherThanTruncatedCoordinates() {
        assertEquals("整宗（1024区块）", LandScopeLabels.label("parcel", 1024));
        assertEquals("选区（2147483647区块）", LandScopeLabels.label("chunks", Integer.MAX_VALUE));
    }
}
