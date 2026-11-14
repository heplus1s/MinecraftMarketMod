package dev.jdguo.market.api;

/**
 * Result of one savings movement. {@code code} is a stable ASCII token.
 * Player chat is chosen by the market from that token, not from {@code detail}.
 */
public record SettlementResult(boolean success, String code, String detail) {
    public SettlementResult {
        code = code == null ? "" : code;
        detail = detail == null ? "" : detail;
    }

    public static SettlementResult ok() {
        return new SettlementResult(true, "ok", "");
    }

    public static SettlementResult fail(String code, String detail) {
        return new SettlementResult(false, code == null ? "failed" : code, detail);
    }
}
