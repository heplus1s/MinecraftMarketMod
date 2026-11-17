package dev.jdguo.market.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketCatalogTest {
    @Test void specialItemsEquipmentAndModItemsRemainBrowsable() {
        for (String id : new String[]{"minecraft:diamond_sword", "minecraft:netherite_helmet", "minecraft:elytra",
                "minecraft:beacon", "minecraft:observer", "minecraft:shulker_box", "example:custom_tool"})
            assertTrue(MarketCatalog.visible(id), id);
    }
    @Test void airAndOperatorInfrastructureAreNotAdvertised() {
        for (String id : new String[]{"minecraft:air", "minecraft:command_block", "minecraft:jigsaw", "minecraft:structure_block"})
            assertFalse(MarketCatalog.visible(id), id);
    }
}
