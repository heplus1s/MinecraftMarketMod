package dev.jdguo.market.integrate;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SystemShopBulkTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ITEM = "minecraft:stone";
    private static final String TOKEN = "10000000-0000-0000-0000-000000000001";
    private static int previewCalls;
    private static int executionCalls;
    private static String previewToken;
    private static boolean throwAfterCall;

    @BeforeEach
    void reset() {
        previewCalls = executionCalls = 0;
        previewToken = TOKEN;
        throwAfterCall = false;
    }

    @Test
    void absentOlderOrPartialShopIsNotInvoked() {
        for (Class<?> api : new Class<?>[]{null, LegacyShop.class, PreviewOnlyShop.class}) {
            assertFalse(SystemShopBulk.exchange(api, PLAYER, ITEM, null, true).ok());
            assertFalse(SystemShopBulk.exchange(api, PLAYER, ITEM, TOKEN, false).ok());
        }
        assertEquals(0, previewCalls);
        assertEquals(0, executionCalls);
    }

    @Test
    void previewReturnsDisplayAndConfirmationThenExecuteDelegatesOnlyOnce() {
        var quote = SystemShopBulk.exchange(NewShop.class, PLAYER, ITEM, null, true);
        assertTrue(quote.ok());
        assertEquals(TOKEN, quote.confirmation());
        assertEquals(128, quote.count());
        assertEquals("石头", quote.itemName());
        assertEquals("央行券", quote.currency());
        assertEquals("98.4321", quote.total());
        assertEquals(1, previewCalls);
        assertEquals(0, executionCalls);
        var sale = SystemShopBulk.exchange(NewShop.class, PLAYER, ITEM, TOKEN, false);
        assertTrue(sale.ok());
        assertEquals("成交", sale.message());
        assertEquals(1, previewCalls);
        assertEquals(1, executionCalls);
    }

    @Test
    void rejectsMalformedConfirmationAndDoesNotRetryUncertainExecution() {
        previewToken = TOKEN + " extra";
        assertFalse(SystemShopBulk.exchange(NewShop.class, PLAYER, ITEM, null, true).ok());
        assertFalse(SystemShopBulk.exchange(NewShop.class, PLAYER, ITEM, previewToken, false).ok());
        assertEquals(0, executionCalls);
        throwAfterCall = true;
        var result = SystemShopBulk.exchange(NewShop.class, PLAYER, ITEM, TOKEN, false);
        assertFalse(result.ok());
        assertTrue(result.message().contains("不要重复提交"));
        assertEquals(1, executionCalls);
    }

    public static class LegacyShop {
        public static String quote(UUID player, String item, int count, boolean buying) {
            previewCalls++;
            return "{}";
        }
        public static String execute(UUID player, String item, int count, boolean buying, java.math.BigDecimal total) {
            executionCalls++;
            return "{}";
        }
    }

    public static class PreviewOnlyShop {
        public static String quoteBulk(UUID player, String item) {
            previewCalls++;
            return "{}";
        }
    }

    public static class NewShop {
        public static String quoteBulk(UUID player, String item) {
            previewCalls++;
            assertEquals(PLAYER, player);
            assertEquals(ITEM, item);
            var result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("message", "报价");
            result.addProperty("itemId", item);
            result.addProperty("itemName", "石头");
            result.addProperty("currencyName", "央行券");
            result.addProperty("total", "98.4321");
            result.addProperty("count", 128);
            result.addProperty("confirmation", previewToken);
            return result.toString();
        }
        public static String executeBulk(UUID player, String item, String token) {
            executionCalls++;
            assertEquals(PLAYER, player);
            assertEquals(ITEM, item);
            assertEquals(TOKEN, token);
            if (throwAfterCall) throw new IllegalStateException();
            return "{\"ok\":true,\"message\":\"成交\"}";
        }
    }
}
