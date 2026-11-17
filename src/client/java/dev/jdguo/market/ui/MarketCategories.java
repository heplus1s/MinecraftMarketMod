package dev.jdguo.market.ui;

import java.util.List;

/** Shared shop category ids; labels remain useful when the optional shop is absent. */
public final class MarketCategories {
    private MarketCategories() {}
    public static final List<String> ORDER = List.of("全部", "natural", "building", "colored", "functional", "redstone", "transport", "food", "tools", "weapons", "armor", "materials", "other", "blocks", "land");
    public static String label(String key) {
        return switch (key) {
            case "natural" -> "自然方块"; case "building" -> "建筑方块"; case "colored" -> "染色方块";
            case "functional" -> "功能方块"; case "redstone" -> "红石与机械"; case "transport" -> "交通运输";
            case "tools" -> "工具"; case "weapons" -> "武器"; case "armor" -> "护甲";
            case "food" -> "食物与饮品"; case "materials" -> "原材料"; case "other" -> "其他";
            case "blocks" -> "其他方块"; case "land" -> "领地"; default -> key;
        };
    }
}
