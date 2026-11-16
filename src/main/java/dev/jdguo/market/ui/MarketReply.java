package dev.jdguo.market.ui;

import net.minecraft.text.Text;

import java.util.List;

/**
 * What a command, a later chest menu, or the optional UI channel shows after one action.
 * {@code lines} already include the {@code [市场]} prefix. {@code code} is ASCII.
 */
public record MarketReply(boolean ok, String code, List<Text> lines, List<ListingView> views) {
    public MarketReply {
        code = code == null ? "" : code;
        lines = lines == null ? List.of() : List.copyOf(lines);
        views = views == null ? List.of() : List.copyOf(views);
    }

    public static MarketReply of(boolean ok, String code, List<Text> lines, List<ListingView> views) {
        return new MarketReply(ok, code, lines, views);
    }

    public static MarketReply message(boolean ok, String code, Text line) {
        return new MarketReply(ok, code, List.of(line), List.of());
    }
}
