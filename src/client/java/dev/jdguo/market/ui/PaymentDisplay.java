package dev.jdguo.market.ui;
import java.math.BigDecimal;
/** Never invent or hide a second tender; zero residuals need no confusing label. */
public final class PaymentDisplay {
    private PaymentDisplay() {}
    public static boolean positive(String value) {
        try { return value != null && new BigDecimal(value).signum() > 0; }
        catch (RuntimeException malformed) { return false; }
    }
    public static String extra(String value, String currencyName) {
        return positive(value) ? "；另付 " + value + " " + currencyName : "";
    }
}
