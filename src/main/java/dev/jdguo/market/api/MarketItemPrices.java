package dev.jdguo.market.api;

import dev.jdguo.market.MarketRuntime;
import dev.jdguo.market.world.MarketSavedData;

import java.math.BigDecimal;

/** Recent independently priced standard-item sales. Called on the server thread. */
public final class MarketItemPrices {
    private MarketItemPrices() {
    }

    public static BigDecimal recentUnitPrice(String itemId, String currency) {
        var server = MarketRuntime.server();
        if (server == null || !server.isOnThread() || itemId == null || currency == null) {
            return null;
        }
        return MarketSavedData.get(server).book().recentItemUnitPrice(itemId, currency, System.currentTimeMillis());
    }

    /**
     * Qualified realized units in the last 24 hours, for inventory planning only.
     * Zero means no reliable signal (including when the world or server thread is unavailable).
     * This is traded volume, not net buy pressure or a quote.
     */
    public static long recentDemandUnits(String itemId, String currency) {
        var server = MarketRuntime.server();
        if (server == null || !server.isOnThread() || itemId == null || currency == null) {
            return 0;
        }
        return MarketSavedData.get(server).book().recentDemandUnits(itemId, currency, System.currentTimeMillis());
    }
}
