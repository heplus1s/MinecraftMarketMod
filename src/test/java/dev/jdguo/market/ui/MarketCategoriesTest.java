package dev.jdguo.market.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketCategoriesTest {
    @Test void sharedCategoriesHaveReadableLabels() {
        for (var id : MarketCategories.ORDER) if (!id.equals("全部")) assertNotEquals(id, MarketCategories.label(id));
    }
    @Test void categoryIdsAreUnique() { assertEquals(MarketCategories.ORDER.size(), new java.util.HashSet<>(MarketCategories.ORDER).size()); }
    @Test void unknownCategoriesRemainAccessible() { assertEquals("custom", MarketCategories.label("custom")); }
}
