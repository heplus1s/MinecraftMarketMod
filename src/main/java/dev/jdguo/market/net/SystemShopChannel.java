package dev.jdguo.market.net;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.*;

/** Optional system bid/ask execution; the shop alone moves inventory and money. */
public final class SystemShopChannel {
    public static final Identifier CHANNEL = new Identifier("marketmod", "system_shop");
    private static final SystemShopConfirmations pending = new SystemShopConfirmations();
    private SystemShopChannel() {}
    public static void disconnect(UUID player) { pending.remove(player); }
    public static boolean available() {
        try { Class.forName("dev.jdguo.shop.api.ShopMarket"); return true; }
        catch (ClassNotFoundException | LinkageError e) { return false; }
    }
    private static Object call(String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        return Class.forName("dev.jdguo.shop.api.ShopMarket").getMethod(name, types).invoke(null, args);
    }
    public static JsonArray trades() {
        try { return JsonParser.parseString((String) call("recentTrades", new Class<?>[]{})).getAsJsonArray(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) { return new JsonArray(); }
    }
    public static void act(ServerPlayerEntity player, String[] parts) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) return;
        JsonObject out = new JsonObject();
        try {
            if (parts.length == 4 && parts[0].equals("shopquote")) {
                pending.remove(player.getUuid());
                if (!parts[2].matches("[1-9][0-9]{0,3}") || (!parts[3].equals("buy") && !parts[3].equals("sell"))) throw new IllegalArgumentException();
                int count = Integer.parseInt(parts[2]); boolean buying = parts[3].equals("buy");
                out = JsonParser.parseString((String)call("quote", new Class<?>[]{UUID.class,String.class,int.class,boolean.class}, player.getUuid(),parts[1],count,buying)).getAsJsonObject();
                if (out.get("ok").getAsBoolean()) {
                    String token = pending.put(player.getUuid(),parts[1],count,buying,new BigDecimal(out.get("total").getAsString()),System.currentTimeMillis()).token();
                    out.addProperty("token", token);
                }
            } else if (parts.length == 2 && parts[0].equals("shopconfirm")) {
                var order = pending.consume(player.getUuid(),parts[1],System.currentTimeMillis());
                if (order == null) throw new IllegalArgumentException();
                out = JsonParser.parseString((String)call("execute",new Class<?>[]{UUID.class,String.class,int.class,boolean.class,BigDecimal.class},player.getUuid(),order.item(),order.count(),order.buying(),order.total())).getAsJsonObject();
                out.addProperty("completed",true);
                MarketSync.broadcast(player.getServer());
            } else return;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            out.addProperty("ok",false);out.addProperty("message","商店接口或确认已失效，请重新询价；若已提交，请先核对余额和物品。");
        }
        var buf=PacketByteBufs.create();buf.writeString(out.toString(),32767);ServerPlayNetworking.send(player,CHANNEL,buf);
    }
}
