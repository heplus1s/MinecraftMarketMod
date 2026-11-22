package dev.jdguo.market.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.market.MarketMod;
import dev.jdguo.market.api.GovernmentRepurchase;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.message.MarketMessages;
import dev.jdguo.market.ui.LandScopeLabels;
import dev.jdguo.market.world.MarketSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class GovernmentRepurchaseChannel {
    public static final Identifier UPDATE = new Identifier("marketmod", "repurchase_ui");
    private static final int PAGE_SIZE = 8;

    private GovernmentRepurchaseChannel() {}

    public static boolean supports(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, UPDATE);
    }

    public static void act(ServerPlayerEntity player, String[] args) {
        if (!supports(player) || !player.hasPermissionLevel(2)) return;
        execute(player, args.length == 3 ? args[1] : "", args.length == 3 ? args[2] : "", false);
    }

    public static void open(ServerPlayerEntity player, String listing) {
        if (!supports(player) || !player.hasPermissionLevel(2)) return;
        if (player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();
        execute(player, listing == null ? "list" : "preview", listing == null ? "0" : listing, true);
    }

    private static void execute(ServerPlayerEntity player, String action, String value, boolean open) {
        JsonObject payload = new JsonObject();
        payload.addProperty("open", open);
        try {
            switch (action) {
                case "list" -> {
                    int requested = Integer.parseInt(value);
                    var listings = MarketSavedData.get(player.getServer()).book().search(null, null, null, true)
                            .stream().filter(GovernmentRepurchase::eligible).toList();
                    int pages = Math.max(1, (listings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                    int page = Math.max(0, Math.min(requested, pages - 1));
                    JsonArray rows = new JsonArray();
                    for (int i = page * PAGE_SIZE; i < Math.min(listings.size(), (page + 1) * PAGE_SIZE); i++)
                        rows.add(row(listings.get(i), false));
                    payload.addProperty("type", "list");
                    payload.addProperty("page", page);
                    payload.addProperty("pages", pages);
                    payload.addProperty("count", listings.size());
                    payload.add("rows", rows);
                }
                case "preview" -> {
                    var quote = GovernmentRepurchase.preview(player, value);
                    payload.addProperty("type", "preview");
                    payload.addProperty("token", quote.token().toString());
                    payload.add("row", row(quote.listing(), true));
                }
                case "confirm" -> {
                    payload.addProperty("type", "result");
                    payload.addProperty("message", GovernmentRepurchase.confirmToken(player, UUID.fromString(value)));
                }
                default -> throw new IllegalArgumentException("无效的回购操作。");
            }
        } catch (IllegalArgumentException invalid) {
            payload = error(open, "confirm".equals(action), invalid instanceof NumberFormatException ? "无效的页码。" : invalid.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            MarketMod.LOGGER.error("Could not handle the system land repurchase screen", failure);
            payload = error(open, "confirm".equals(action), "服务器暂无法确认操作，请检查挂牌与交易记录。");
        }
        MarketSync.send(player, UPDATE, payload.toString());
    }

    private static JsonObject error(boolean open, boolean submitted, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("open", open);
        payload.addProperty("type", submitted ? "result" : "error");
        payload.addProperty("message", message);
        return payload;
    }

    private static JsonObject row(Listing listing, boolean includeChunks) {
        JsonObject row = new JsonObject();
        row.addProperty("id", listing.id().toString());
        row.addProperty("name", listing.parcelName());
        row.addProperty("seller", listing.sellerName());
        row.addProperty("dimension", listing.dimension());
        row.addProperty("scope", LandScopeLabels.label(listing.kind().token(), listing.chunks().size()));
        row.addProperty("price", listing.customTotal().toString());
        row.addProperty("currency", MarketMessages.currency(listing.currency()));
        if (includeChunks) {
            JsonArray chunks = new JsonArray();
            for (var chunk : listing.chunks()) chunks.add(chunk.x() + ", " + chunk.z());
            row.add("chunks", chunks);
        }
        return row;
    }
}
