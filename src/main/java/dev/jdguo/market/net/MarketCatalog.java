package dev.jdguo.market.net;

import com.google.gson.JsonObject;
import dev.jdguo.market.integrate.ShopPrices;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Browsing is independent of escrow: a catalog entry is never a sell order. */
public final class MarketCatalog {
    private static final Set<String> OP_ONLY = Set.of("minecraft:air", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:structure_void", "minecraft:barrier", "minecraft:light");
    private MarketCatalog() {}
    public static boolean visible(String id) { return !OP_ONLY.contains(id); }
    public static List<JsonObject> entries(String currency) {
        List<JsonObject> rows = new ArrayList<>();
        Registries.ITEM.getIds().stream().sorted().forEach(id -> {
            if (!visible(id.toString())) return;
            Item item = Registries.ITEM.get(id);
            JsonObject row = new JsonObject();
            row.addProperty("itemId", id.toString());
            row.addProperty("itemName", item.getName().getString());
            row.addProperty("translationKey", item.getTranslationKey());
            String shopCategory = ShopPrices.categoryId(id.toString());
            row.addProperty("category", shopCategory.isBlank() ? category(item) : shopCategory);
            row.addProperty("variantKey", "default");
            row.addProperty("referenceCurrency", currency);
            var bid = ShopPrices.exactQuote(id.toString(), currency, false);
            var ask = ShopPrices.exactQuote(id.toString(), currency, true);
            row.addProperty("shopBid", bid == null ? "" : bid.stripTrailingZeros().toPlainString());
            row.addProperty("shopAsk", ask == null ? "" : ask.stripTrailingZeros().toPlainString());
            rows.add(row);
        });
        return rows;
    }
    private static String category(Item item) {
        if (item instanceof ArmorItem) return "armor";
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return "weapons";
        if (item instanceof ToolItem || item instanceof ShearsItem || item instanceof FishingRodItem || item instanceof FlintAndSteelItem) return "tools";
        if (item instanceof BlockItem) return "blocks";
        if (item.isFood()) return "food";
        return "other";
    }
}
