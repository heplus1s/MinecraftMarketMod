package dev.jdguo.market.message;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class MarketMessages {
    public static final String PREFIX = "[市场] ";

    private MarketMessages() {
    }

    public static MutableText line(String body) {
        return Text.literal(PREFIX + body);
    }

    public static MutableText line(String before, Text middle, String after) {
        return Text.literal(PREFIX + before).append(middle).append(Text.literal(after));
    }

    public static String currency(String id) {
        if (id == null) return "";
        return dev.jdguo.market.integrate.MarketCurrencies.options().stream()
                .filter(option -> option.id().equals(id)).map(dev.jdguo.market.integrate.MarketCurrencies.Option::label)
                .findFirst().orElseGet(() -> dev.jdguo.market.integrate.MarketCurrencies.fallbackLabel(id));
    }

    public static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> id == null || id.isBlank() ? "未知维度" : id;
        };
    }

    public static String parcelName(String name) {
        return name == null || name.isBlank() ? "未命名宗地" : name;
    }
}
