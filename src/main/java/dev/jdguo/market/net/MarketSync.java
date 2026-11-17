package dev.jdguo.market.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.ui.ListingView;
import dev.jdguo.market.ui.MarketReply;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional channel for a later client UI. The server understands it. A packet
 * is sent only after that client registers the channel, so a vanilla client
 * can join and is never sent a payload it does not know.
 * <p>
 * Protocol 1. Client to server: {@code marketmod:hello} (varint version) and
 * {@code marketmod:action} (string). Server to client: {@code marketmod:sync}
 * and {@code marketmod:result}. Actions use the same rules as the commands.
 */
public final class MarketSync {
    public static final int PROTOCOL = 1;
    public static final Identifier OPEN = new Identifier("marketmod", "open");
    public static final Identifier HELLO = new Identifier("marketmod", "hello");
    public static final Identifier ACTION = new Identifier("marketmod", "action");
    public static final Identifier SYNC = new Identifier("marketmod", "sync");
    public static final Identifier RESULT = new Identifier("marketmod", "result");

    private static final Set<UUID> LISTENING = ConcurrentHashMap.newKeySet();

    private MarketSync() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, response) -> {
            int version;
            try {
                version = buf.readVarInt();
            } catch (RuntimeException exception) {
                return;
            }
            server.execute(() -> {
                if (version == PROTOCOL) {
                    LISTENING.add(player.getUuid());
                    broadcast(server);
                } else {
                    LISTENING.remove(player.getUuid());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, response) -> {
            String action;
            try {
                action = buf.readString(512);
            } catch (RuntimeException exception) {
                return;
            }
            server.execute(() -> act(player, action));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LISTENING.remove(handler.player.getUuid()));
    }

    public static boolean open(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, OPEN) || !ServerPlayNetworking.canSend(player, SYNC) || !ServerPlayNetworking.canSend(player, RESULT)) return false;
        LISTENING.add(player.getUuid());
        send(player, OPEN, snapshot(MarketDesk.get(player.getServer())));
        return true;
    }

    public static void broadcast(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (LISTENING.contains(player.getUuid()) && ServerPlayNetworking.canSend(player, SYNC)) {
                send(player, SYNC, snapshot(MarketDesk.get(server)));
            }
        }
    }

    public static void result(ServerPlayerEntity player, MarketReply reply) {
        if (player == null || reply == null || !LISTENING.contains(player.getUuid()) || !ServerPlayNetworking.canSend(player, RESULT)) {
            return;
        }
        JsonObject object = new JsonObject();
        object.addProperty("ok", reply.ok());
        object.addProperty("code", reply.code());
        JsonArray lines = new JsonArray();
        for (Text line : reply.lines()) {
            lines.add(line.getString());
        }
        object.add("lines", lines);
        send(player, RESULT, object.toString());
    }

    private static void act(ServerPlayerEntity player, String action) {
        if (!LISTENING.contains(player.getUuid()) || !ServerPlayNetworking.canSend(player, SYNC)) return;
        String[] parts = action.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return;
        }
        MarketDesk desk = MarketDesk.get(player.getServer());
        MarketReply reply = switch (parts[0]) {
            case "buy" -> buy(desk, player, parts);
            case "cancel" -> parts.length >= 2 ? desk.cancel(player, parts[1], player.hasPermissionLevel(2)) : unknown();
            case "info" -> parts.length >= 2 ? desk.info(player, parts[1]) : unknown();
            case "price" -> desk.price(player);
            case "search" -> search(desk, player, parts);
            case "list" -> list(desk, player, parts);
            case "listshop" -> parts.length == 4 && number(parts[1]) > 0 && number(parts[3]) > 0 ? desk.listItem(player, true, 0, number(parts[1]), parts[2], number(parts[3])) : unknown();
            case "land" -> land(desk, player, parts, false);
            case "server" -> player.hasPermissionLevel(2) ? land(desk, player, parts, true) : unknown();
            default -> unknown();
        };
        result(player, reply);
    }

    private static MarketReply buy(MarketDesk desk, ServerPlayerEntity player, String[] parts) {
        if (parts.length == 2) return desk.buy(player, parts[1]); // old protocol client
        if (parts.length == 3 && parts[2].matches("[0-9]{1,64}")) {
            return desk.buy(player, parts[1], new java.math.BigInteger(parts[2]));
        }
        if (parts.length == 4 && parts[2].matches("[0-9]{1,64}") && number(parts[3]) > 0)
            return desk.buy(player, parts[1], new java.math.BigInteger(parts[2]), number(parts[3]));
        return unknown();
    }

    private static MarketReply search(MarketDesk desk, ServerPlayerEntity player, String[] parts) {
        if (parts.length == 1) {
            return desk.search(player, "all", "", 1);
        }
        return switch (parts[1]) {
            case "item" -> desk.search(player, "item", parts.length >= 3 ? parts[2] : "", page(parts, 3));
            case "player" -> desk.search(player, "player", parts.length >= 3 ? parts[2] : "", page(parts, 3));
            case "land" -> desk.search(player, "land", "", page(parts, 2));
            case "mine" -> desk.search(player, "mine", "", page(parts, 2));
            default -> desk.search(player, "all", "", page(parts, 1));
        };
    }

    private static MarketReply list(MarketDesk desk, ServerPlayerEntity player, String[] parts) {
        if (parts.length > 4 || (parts.length >= 3 && number(parts[2]) <= 0)) return unknown();
        if (parts.length >= 2 && "shop".equals(parts[1])) {
            int count = parts.length >= 3 ? number(parts[2]) : 0;
            return desk.listItem(player, true, 0, count, currency(parts, 3));
        }
        if (parts.length >= 2) {
            long price = positive(parts[1]);
            int count = parts.length >= 3 ? number(parts[2]) : 0;
            if (price <= 0) {
                return unknown();
            }
            return desk.listItem(player, false, price, count, currency(parts, 3));
        }
        return unknown();
    }

    private static MarketReply land(MarketDesk desk, ServerPlayerEntity player, String[] parts, boolean serverSale) {
        if (parts.length < 2) {
            return unknown();
        }
        return switch (parts[1]) {
            case "parcel" -> parts.length >= 3 ? desk.listParcel(player, positive(parts[2]), serverSale, currency(parts, 3)) : unknown();
            case "chunk" -> parts.length >= 3 ? desk.listChunk(player, positive(parts[2]), serverSale, currency(parts, 3)) : unknown();
            case "chunks" -> rectangle(desk, player, parts, serverSale);
            default -> unknown();
        };
    }

    private static String currency(String[] parts, int index) {
        return parts.length > index ? parts[index] : dev.jdguo.market.config.MarketConfig.get().currency();
    }

    private static MarketReply unknown() {
        return MarketReply.message(false, "syntax", dev.jdguo.market.message.MarketMessages.line("这个操作无法识别。"));
    }

    private static MarketReply rectangle(MarketDesk desk, ServerPlayerEntity player, String[] parts, boolean serverSale) {
        if (parts.length < 7) {
            return unknown();
        }
        Long price = maybeLong(parts[2]);
        Integer x1 = maybeInt(parts[3]);
        Integer z1 = maybeInt(parts[4]);
        Integer x2 = maybeInt(parts[5]);
        Integer z2 = maybeInt(parts[6]);
        if (price == null || price <= 0 || x1 == null || z1 == null || x2 == null || z2 == null) {
            return unknown();
        }
        return desk.listRectangle(player, price, serverSale, x1, z1, x2, z2, currency(parts, 7));
    }

    private static Integer maybeInt(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long maybeLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int page(String[] parts, int index) {
        if (index >= parts.length) {
            return 1;
        }
        int value = number(parts[index]);
        return value <= 0 ? 1 : value;
    }

    private static int number(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static long positive(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String snapshot(MarketDesk desk) {
        JsonObject root = new JsonObject();
        root.addProperty("protocol", PROTOCOL);
        root.addProperty("explicitCurrency", true);
        root.addProperty("maximumTotal", true);
        root.addProperty("partialItems", true);
        root.addProperty("bulkLimit", 2304);
        root.addProperty("defaultCurrency", dev.jdguo.market.config.MarketConfig.get().currency());
        JsonArray currencies = new JsonArray();
        for (var option : dev.jdguo.market.integrate.MarketCurrencies.options()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", option.id()); value.addProperty("label", option.label());
            currencies.add(value);
        }
        root.add("currencies", currencies);
        JsonArray listings = new JsonArray();
        var open = desk.book().search(null, null, null, false);
        root.addProperty("totalListings", open.size());
        root.addProperty("truncated", open.size() > 256);
        int kept = 0;
        for (var listing : open) {
            ListingView view = desk.view(listing, listing.priceMode() == dev.jdguo.market.listing.PriceMode.SHOP
                    ? dev.jdguo.market.integrate.ShopPrices.quote(listing) : null);
            if (kept++ >= 256) {
                break;
            }
            JsonObject one = new JsonObject();
            one.addProperty("id", view.id());
            one.addProperty("shortId", view.shortId());
            one.addProperty("kind", view.kind());
            one.addProperty("sellerName", view.sellerName());
            one.addProperty("sellerId", view.sellerId());
            one.addProperty("landGrant", view.landGrant());
            one.addProperty("shopPrice", view.shopPrice());
            one.addProperty("unitPrice", view.unitPrice());
            one.addProperty("totalPrice", view.totalPrice());
            one.addProperty("currency", view.currency());
            one.addProperty("count", view.count());
            one.addProperty("itemId", view.itemId());
            one.addProperty("itemName", listing.kind() == dev.jdguo.market.listing.ListingKind.ITEM
                    ? dev.jdguo.market.world.ItemDelivery.read(listing.item()).getName().getString() : view.parcelName());
            one.addProperty("shopPercent", listing.shopPercent());
            one.addProperty("currencyLabel", dev.jdguo.market.message.MarketMessages.currency(view.currency()));
            var shopBid = dev.jdguo.market.integrate.ShopPrices.exactQuote(listing.itemId(), listing.currency(), false);
            var shopAsk = dev.jdguo.market.integrate.ShopPrices.exactQuote(listing.itemId(), listing.currency(), true);
            one.addProperty("shopBid", shopBid == null ? "" : shopBid.stripTrailingZeros().toPlainString());
            one.addProperty("shopAsk", shopAsk == null ? "" : shopAsk.stripTrailingZeros().toPlainString());
            one.addProperty("dimension", view.dimension());
            one.addProperty("parcelId", view.parcelId());
            one.addProperty("parcelName", view.parcelName());
            JsonArray chunks = new JsonArray();
            view.chunks().stream().limit(64).forEach(chunks::add);
            one.addProperty("chunksTruncated", view.chunks().size() > 64);
            one.add("chunks", chunks);
            if (listings.toString().length() + one.toString().length() > 175000) {
                root.addProperty("truncated", true);
                break;
            }
            listings.add(one);
        }
        root.add("listings", listings);
        JsonArray trades = new JsonArray();
        for (var trade : desk.book().recentTrades()) {
            if (trade.getString("ItemId").isBlank() || trade.getInt("Count") <= 0) continue;
            JsonObject t = new JsonObject();
            t.addProperty("itemId", trade.getString("ItemId")); t.addProperty("currency", trade.getString("Currency"));
            t.addProperty("count", trade.getInt("Count")); t.addProperty("amount", trade.getString("Amount")); t.addProperty("at", trade.getLong("At"));
            trades.add(t);
        }
        root.add("trades", trades);
        return root.toString();
    }

    private static void send(ServerPlayerEntity player, Identifier channel, String json) {
        if (!ServerPlayNetworking.canSend(player, channel) || json.length() > 200000) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(json, 262144);
        ServerPlayNetworking.send(player, channel, buf);
    }
}
