package dev.jdguo.market.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.market.integrate.MarketCurrencies;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.listing.ChunkRef;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.math.BigInteger;
import java.util.*;

/** Optional native confirmation. The client never supplies the purchase price or scope. */
public final class LandUiChannel {
    public static final Identifier OPEN = new Identifier("marketmod", "land_open");
    public static final Identifier ACTION = new Identifier("marketmod", "land_action");
    public static final Identifier RESULT = new Identifier("marketmod", "land_result");
    private record Pending(UUID token, String action, UUID parcel, String dimension, int[] coordinates, JsonObject quote, JsonObject payment, long expires) {}
    private static final Map<UUID, Pending> pending = new HashMap<>();
    private LandUiChannel() {}
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> pending.remove(handler.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, response) -> {
            final String json;
            try { json = buf.readString(4096); } catch (RuntimeException bad) { return; }
            server.execute(() -> {
                if (server.getPlayerManager().getPlayer(player.getUuid()) != player || player.networkHandler != handler) return;
                try {
                    JsonObject input = JsonParser.parseString(json).getAsJsonObject();
                    Pending p = pending.get(player.getUuid());
                    if (p == null || !p.token().toString().equals(input.get("token").getAsString())) { result(player, "确认已失效，请重新打开", false); return; }
                    if ("quote".equals(input.get("action").getAsString())) {
                        if (!p.action().equals("buy") || !"initial".equals(p.quote().get("kind").getAsString()) || server.getTicks() > p.expires()) return;
                        String tender = input.get("currency").getAsString();
                        if (!MarketCurrencies.acceptsLand(tender)) return;
                        open(player, p.action(), p.parcel(), p.dimension(), p.coordinates(), p.quote(), tender); return;
                    }
                    // Remove before execution, including exceptions: a repeated packet cannot pay twice.
                    pending.remove(player.getUuid());
                    if ("cancel".equals(input.get("action").getAsString())) return;
                    if (!player.isAlive() || server.getTicks() > p.expires()) { result(player, "确认已过期，请重新打开", false); return; }
                    String message;
                    boolean ok;
                    if (p.action().equals("sell")) {
                        String currency = input.get("currency").getAsString();
                        if (!MarketCurrencies.acceptsLand(currency)) throw new IllegalArgumentException();
                        long price = Long.parseLong(input.get("price").getAsString());
                        if (price <= 0) throw new IllegalArgumentException();
                        List<ChunkRef> chunks = new ArrayList<>();
                        for (int i = 0; i < p.coordinates().length; i += 2) chunks.add(new ChunkRef(p.dimension(), p.coordinates()[i], p.coordinates()[i + 1]));
                        var reply = MarketDesk.get(server).listSelectedLand(player, p.parcel(), chunks, price, currency);
                        reply.lines().forEach(line -> player.sendMessage(line, false));
                        ok = reply.ok(); message = ok ? "领地挂牌成功" : "挂牌失败，请查看交易提示并重新打开";
                    } else if ("initial".equals(p.quote().get("kind").getAsString())) {
                        if (p.payment() != null && (!p.payment().has("success") || !p.payment().get("success").getAsBoolean())) { result(player, "付款报价不可用，请重新选择", false); return; }
                        message = LandMarketTerminal.confirmInitial(player, p.dimension(), p.coordinates(), p.quote(),
                                p.payment() == null ? dev.jdguo.market.integrate.BankBridge.payments() : dev.jdguo.market.integrate.BankBridge.landPayments(p.payment().get("paymentCurrency").getAsString(), p.payment().get("fingerprint").getAsString()));
                        ok = message.equals("土地购买成功");
                    } else {
                        JsonObject fresh = JsonParser.parseString(LandMarketTerminal.quote(server, player.getUuid(), p.dimension(), p.coordinates())).getAsJsonObject();
                        if (!LandQuoteMatch.matches(p.quote(), fresh)) { result(player, "价格或范围已变化，请重新打开确认", false); return; }
                        var reply = MarketDesk.get(server).buy(player, p.quote().get("listingId").getAsString(), new BigInteger(p.quote().get("total").getAsString()));
                        reply.lines().forEach(line -> player.sendMessage(line, false));
                        ok = reply.ok(); message = ok ? "领地购买成功" : "购买未完成，请查看交易提示";
                    }
                    result(player, message, ok);
                } catch (RuntimeException error) {
                    result(player, "交易未完成或结果待核验，请查看提示，不要重复付款", false);
                }
            });
        });
    }
    public static boolean open(ServerPlayerEntity player, String action, UUID parcel, String dimension, int[] coordinates, JsonObject quote) {
        return open(player, action, parcel, dimension, coordinates, quote, quote.get("currency").getAsString());
    }
    private static boolean open(ServerPlayerEntity player, String action, UUID parcel, String dimension, int[] coordinates, JsonObject quote, String tender) {
        if (!ServerPlayNetworking.canSend(player, OPEN) || !ServerPlayNetworking.canSend(player, RESULT)) return false;
        UUID token = UUID.randomUUID();
        boolean initial = action.equals("buy") && quote.has("kind") && "initial".equals(quote.get("kind").getAsString());
        JsonObject payment = initial ? dev.jdguo.market.integrate.BankBridge.landQuote(quote.get("currency").getAsString(), new BigInteger(quote.get("total").getAsString()), tender) : null;
        // An older bank retains the existing same-currency path; never advertise unavailable conversion.
        if (payment != null && !payment.has("success")) { payment = null; initial = false; tender = quote.get("currency").getAsString(); }
        pending.put(player.getUuid(), new Pending(token, action, parcel, dimension, coordinates.clone(), quote.deepCopy(), payment, player.getServer().getTicks() + 1200L));
        JsonObject out = quote.deepCopy();
        out.addProperty("paymentCurrency", tender); out.addProperty("crossPayment", initial);
        if (payment != null) { JsonObject shown = payment.deepCopy(); shown.remove("fingerprint"); out.add("payment", shown); }
        out.addProperty("token", token.toString()); out.addProperty("action", action); out.addProperty("dimension", dimension);
        var options = new com.google.gson.JsonArray();
        for (var c : MarketCurrencies.landOptions()) {
            // Buyers pay the seller's declared currency; listing creation can select any supported currency.
            if (!action.equals("sell") && !initial && !c.id().equals(out.get("currency").getAsString())) continue;
            JsonObject option = new JsonObject(); option.addProperty("id", c.id()); option.addProperty("label", c.label()); options.add(option);
        }
        out.add("currencies", options);
        var packet = PacketByteBufs.create(); packet.writeString(out.toString(), 32767); ServerPlayNetworking.send(player, OPEN, packet); return true;
    }
    private static void result(ServerPlayerEntity player, String message, boolean ok) {
        if (!ServerPlayNetworking.canSend(player, RESULT)) return;
        JsonObject out = new JsonObject(); out.addProperty("message", message); out.addProperty("ok", ok);
        var packet = PacketByteBufs.create(); packet.writeString(out.toString(), 4096); ServerPlayNetworking.send(player, RESULT, packet);
    }
}
