package dev.jdguo.market.integrate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

/** Optional server-side bridge. Shop is the only owner of confirmations, settlement and inventory. */
public final class SystemShopBulk {
    private static final String SHOP_API = "dev.jdguo.shop.api.ShopMarket";
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    private SystemShopBulk() {}

    public static Result quote(UUID player, String item) {
        return exchange(shopApi(), player, item, null, true);
    }

    public static Result execute(UUID player, String item, String token) {
        return exchange(shopApi(), player, item, token, false);
    }

    private static Class<?> shopApi() {
        try {
            return Class.forName(SHOP_API, false, SystemShopBulk.class.getClassLoader());
        } catch (ClassNotFoundException | SecurityException | LinkageError unavailable) {
            return null;
        }
    }

    static Result exchange(Class<?> api, UUID player, String item, String token, boolean preview) {
        if (api == null) return missing();
        try {
            var quote = api.getMethod("quoteBulk", UUID.class, String.class);
            var execute = api.getMethod("executeBulk", UUID.class, String.class, String.class);
            if (!Modifier.isStatic(quote.getModifiers()) || !Modifier.isStatic(execute.getModifiers())
                    || quote.getReturnType() != String.class || execute.getReturnType() != String.class) return missing();
            if (!preview && !validToken(token)) return Result.fail("确认编号无效，请重新预览。");
            String raw = (String) (preview ? quote.invoke(null, player, item) : execute.invoke(null, player, item, token));
            JsonObject response = JsonParser.parseString(raw).getAsJsonObject();
            var ok = primitive(response, "ok");
            if (ok == null || !ok.isBoolean()) throw new IllegalArgumentException("missing shop result");
            String message = readString(response, "message");
            if (!ok.getAsBoolean()) {
                String fallback = preview ? "未能取得批量出售报价。" : "商店没有确认成交，请先核对物品和余额。";
                return Result.fail(message.isBlank() ? fallback : message);
            }
            if (!preview) return new Result(true, message, "", 0, "", "", "");
            String confirmation = readString(response, "confirmation");
            String itemName = readString(response, "itemName");
            String currency = readString(response, "currencyName");
            var quantity = primitive(response, "count");
            int count = quantity == null ? 0 : quantity.getAsBigDecimal().intValueExact();
            String total = readString(response, "total");
            if (!validToken(confirmation) || count < 1 || currency.isBlank()
                    || !item.equals(readString(response, "itemId")) || !total.matches("[0-9]{1,128}(?:\\.[0-9]{1,4})?")
                    || new BigDecimal(total).signum() <= 0) return Result.fail("报价资料不完整，未提交出售。");
            return new Result(true, message, confirmation, count, itemName.isBlank() ? item : itemName, currency, total);
        } catch (NoSuchMethodException | SecurityException unavailable) {
            return missing();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Result.fail(preview ? "商店暂时无法预览批量出售，未提交出售。"
                    : "商店未能确认批量出售结果。请先核对盒内物品及余额，不要重复提交。");
        }
    }

    private static boolean validToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    private static JsonPrimitive primitive(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
    }

    private static String readString(JsonObject object, String key) {
        var value = primitive(object, key);
        return value != null && value.isString() ? value.getAsString() : "";
    }

    private static Result missing() {
        return Result.fail("当前服务器的商店未安装或尚不支持潜影盒批量出售。");
    }

    public record Result(boolean ok, String message, String confirmation, int count,
                         String itemName, String currency, String total) {
        private static Result fail(String message) { return new Result(false, message, "", 0, "", "", ""); }
    }
}
