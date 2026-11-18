package dev.jdguo.market.ui;

import java.util.Locale;

/** Display only: never infer parcel ownership or change a listing's server-side scope. */
public final class LandScopeLabels {
    private LandScopeLabels() {}

    public static String label(String kind, int count) {
        if (count <= 0) return "领地选区（范围待同步）";
        String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        String scope = switch (normalized) {
            case "parcel" -> "整宗";
            case "chunks" -> count == 1 ? "单区块" : "选区";
            default -> "领地选区";
        };
        return scope + "（" + count + "区块）";
    }
}
