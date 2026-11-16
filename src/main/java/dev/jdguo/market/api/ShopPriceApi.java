package dev.jdguo.market.api;

import java.math.BigInteger;

/**
 * Live shop reference price for one item. Custom listing prices do not need this.
 * <p>
 * The shop mod can call {@link #install} from its own startup if it depends on
 * this mod. Otherwise it can expose a public static method
 * {@code dev.jdguo.shop.api.ShopPrices.priceEach(String itemId, String currency)}
 * that returns a {@link BigInteger} or null. An entrypoint named
 * {@code marketmod:shop_price} may also provide this interface.
 * <p>
 * {@code currency} is the bank currency id stored on the listing, such as
 * {@code note}. Return null when the shop does not price that item. The market
 * then keeps the listing and tells the buyer the reference price is unavailable.
 * <p>
 * While the shop mod is still absent, a diamond quote already installed on the
 * bank's {@code ShopQuote} is used only when the market currency is {@code diamond}.
 */
public interface ShopPriceApi {
    String SHOP_CLASS = "dev.jdguo.shop.api.ShopPrices";
    String SHOP_METHOD = "priceEach";
    String ENTRYPOINT = "marketmod:shop_price";

    BigInteger priceEach(String itemId, String currency);

    static ShopPriceApi installed() {
        return Holder.api;
    }

    static void install(ShopPriceApi api) {
        Holder.api = api;
    }

    final class Holder {
        private static ShopPriceApi api;

        private Holder() {
        }
    }
}
