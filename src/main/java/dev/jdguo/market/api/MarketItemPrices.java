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
}
