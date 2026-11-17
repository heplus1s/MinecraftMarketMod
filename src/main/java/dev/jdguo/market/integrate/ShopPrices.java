package dev.jdguo.market.integrate;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.api.ShopPriceApi;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Reads a live shop price when one is installed. A missing price leaves custom
 * prices working.
 */
public final class ShopPrices {
    private static volatile Method categoryMethod;
    private static volatile boolean categoryLookedUp;
    private static volatile Method shopMethod;
    private static volatile Method sidedMethod;
    private static volatile boolean sidedLookedUp;
    private static volatile boolean shopLookedUp;

    private ShopPrices() {
    }

    public static BigInteger quote(dev.jdguo.market.listing.Listing listing) {
        BigInteger base = quote(listing.itemId(), listing.currency());
        if (base == null) return null;
        return base.multiply(BigInteger.valueOf(listing.shopPercent())).add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
    }

    public static BigInteger quote(String itemId, String currency) {
        if (itemId == null || itemId.isBlank() || currency == null || currency.isBlank()) {
            return null;
        }
        BigInteger direct = fromApi(ShopPriceApi.installed(), itemId, currency);
        if (direct != null) {
            return direct;
        }
        BigInteger entry = fromApi(entrypoint(), itemId, currency);
        if (entry != null) {
            return entry;
        }
        BigInteger reflected = reflectShop(itemId, currency);
        if (reflected != null) {
            return reflected;
        }
        if ("diamond".equals(currency)) {
            return bankDiamondQuote(itemId);
        }
        return null;
    }

    public static BigDecimal exactQuote(String itemId, String currency, boolean playerBuys) {
        try {
            if (!sidedLookedUp) {
                sidedLookedUp = true;
                sidedMethod = Class.forName(ShopPriceApi.SHOP_CLASS)
                        .getMethod("quoteEach", String.class, String.class, boolean.class);
            }
            if (sidedMethod == null) return null;
            Object quote = sidedMethod.invoke(null, itemId, currency, playerBuys);
            return quote instanceof BigDecimal amount && amount.signum() > 0 ? amount : null;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException error) {
            MarketMod.LOGGER.warn("Shop sided quote failed for {}", itemId, error);
            return null;
        }
    }

    /** Optional metadata API: the shop owns categorization of its configured products. */
    public static String categoryId(String itemId) {
        try {
            if (!categoryLookedUp) {
                categoryLookedUp = true;
                categoryMethod = Class.forName(ShopPriceApi.SHOP_CLASS).getMethod("categoryId", String.class);
            }
            if (categoryMethod == null) return "";
            Object result = categoryMethod.invoke(null, itemId);
            return result instanceof String category ? category : "";
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return ""; }
    }

    public static void resetForTests() {
        categoryLookedUp = false;
        categoryMethod = null;
        shopLookedUp = false;
        sidedLookedUp = false;
        sidedMethod = null;
        shopMethod = null;
        ShopPriceApi.install(null);
    }

    private static BigInteger fromApi(ShopPriceApi api, String itemId, String currency) {
        if (api == null) {
            return null;
        }
        try {
            return positive(api.priceEach(itemId, currency));
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.warn("Shop reference price failed for {}", itemId, exception);
            return null;
        }
    }

    private static ShopPriceApi entrypoint() {
        try {
            List<ShopPriceApi> found = FabricLoader.getInstance().getEntrypoints(ShopPriceApi.ENTRYPOINT, ShopPriceApi.class);
            return found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static BigInteger reflectShop(String itemId, String currency) {
        Method method = shopMethod();
        if (method == null) {
            return null;
        }
        try {
            return positive(asMoney(method.invoke(null, itemId, currency)));
        } catch (ReflectiveOperationException exception) {
            MarketMod.LOGGER.warn("Shop price method failed for {}", itemId, exception);
            return null;
        }
    }

    private static Method shopMethod() {
        if (!shopLookedUp) {
            shopLookedUp = true;
            try {
                Class<?> type = Class.forName(ShopPriceApi.SHOP_CLASS);
                shopMethod = type.getMethod(ShopPriceApi.SHOP_METHOD, String.class, String.class);
            } catch (ClassNotFoundException exception) {
                shopMethod = null;
            } catch (NoSuchMethodException exception) {
                MarketMod.LOGGER.warn("Shop class is present without priceEach(String, String)");
                shopMethod = null;
            }
        }
        return shopMethod;
    }

    private static BigInteger bankDiamondQuote(String itemId) {
        try {
            Class<?> type = Class.forName("dev.jdguo.bank.market.ShopQuote");
            Object installed = type.getMethod("installed").invoke(null);
            if (installed == null) {
                return null;
            }
            Object value = installed.getClass().getMethod("diamondsEach", String.class).invoke(installed, itemId);
            return positive(asMoney(value));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static BigInteger asMoney(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigInteger integer) {
            return integer;
        }
        if (value instanceof BigDecimal decimal) {
            try {
                BigDecimal plain = decimal.stripTrailingZeros();
                if (plain.scale() > 0) {
                    return null;
                }
                return plain.toBigIntegerExact();
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        if (value instanceof Long || value instanceof Integer) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof String raw) {
            try {
                return new BigInteger(raw);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static BigInteger positive(BigInteger value) {
        return value != null && value.signum() > 0 ? value : null;
    }
}
