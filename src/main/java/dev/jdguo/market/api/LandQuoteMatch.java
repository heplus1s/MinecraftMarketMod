package dev.jdguo.market.api;
import com.google.gson.JsonObject;
import java.util.List;
/** Reject stale/scope-swapped confirmations rather than silently using a new price. */
public final class LandQuoteMatch {
    private LandQuoteMatch() {}
    public static boolean matches(JsonObject shown, JsonObject fresh) {
        try {
            if (!fresh.get("buyable").getAsBoolean()) return false;
            for (String key : List.of("kind", "listingId", "revision", "total", "currency", "scope"))
                if (!shown.has(key) || !shown.get(key).equals(fresh.get(key))) return false;
            return true;
        } catch (RuntimeException bad) { return false; }
    }
}
