package dev.jdguo.market.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.integrate.MarketCurrencies;
import dev.jdguo.market.listing.*;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.ui.MarketChestScreen;
import dev.jdguo.market.world.InitialLandSettings;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.math.BigInteger;
import java.util.*;

/** Soft map API. Queries/opening never debit; every purchase uses a second server confirmation. */
public final class LandMarketTerminal {
    private LandMarketTerminal() {}
    public static boolean initialEnabled(MinecraftServer server) {
        return server != null && server.isOnThread() && MarketConfig.get().serverLandSales() && InitialLandSettings.get(server).enabled;
    }
    public static String quote(MinecraftServer server, UUID viewer, String dimension, int[] coordinates) {
        return quoteObject(server, viewer, dimension, coordinates).toString();
    }
    private static JsonObject denied(String reason) {
        JsonObject out = new JsonObject();
        out.addProperty("kind", "unavailable"); out.addProperty("listingId", ""); out.addProperty("revision", "");
        out.addProperty("total", ""); out.addProperty("currency", ""); out.addProperty("currencyName", "");
        out.addProperty("chunks", 0); out.addProperty("buyable", false); out.addProperty("reason", reason);
        out.add("scope", new JsonArray()); return out;
    }
    private static List<ChunkRef> selection(String dimension, int[] coordinates) {
        if (dimension == null || dimension.isBlank() || coordinates == null || coordinates.length == 0 || coordinates.length % 2 != 0
                || coordinates.length > Math.min(1024, MarketConfig.get().maxChunksPerListing()) * 2) throw new IllegalArgumentException("选区数量无效或超过上限");
        Set<ChunkRef> unique = new LinkedHashSet<>();
        for (int i = 0; i < coordinates.length; i += 2) unique.add(new ChunkRef(dimension, coordinates[i], coordinates[i + 1]));
        return List.copyOf(unique);
    }
    private static JsonObject quoteObject(MinecraftServer server, UUID viewer, String dimension, int[] coordinates) {
        if (server == null || viewer == null || !server.isOnThread()) return denied("需要服务器线程");
        try {
            List<ChunkRef> selected = selection(dimension, coordinates);
            if (!LandBridge.available()) return denied("领地模组未安装");
            Listing found = null;
            boolean empty = false;
            for (ChunkRef chunk : selected) {
                var land = LandBridge.at(server, dimension, chunk.x(), chunk.z());
                if (land == null) { empty = true; continue; }
                Listing listing = MarketDesk.get(server).book().listingForParcel(land.id());
                if (listing == null || !land.owner().equals(listing.seller()) || !listing.covers(chunk) || listing.initialGrant()) return denied("所选土地未挂牌或不在挂牌范围内");
                if (found != null && !found.id().equals(listing.id())) return denied("请每次选择一笔挂牌");
                found = listing;
            }
            JsonObject out = denied("");
            if (found != null) {
                if (empty) return denied("不能混合购买挂牌土地与未占用土地");
                out.addProperty("kind", "listing"); out.addProperty("listingId", found.id().toString());
                out.addProperty("revision", Long.toString(found.createdAt()));
                out.addProperty("total", found.customTotal().toString());
                out.addProperty("currency", found.currency()); out.addProperty("currencyName", currencyName(found.currency()));
                scope(out, found.chunks());
                boolean ready = found.status() == ListingStatus.OPEN && !found.seller().equals(viewer);
                out.addProperty("buyable", ready); out.addProperty("reason", ready ? "整笔挂牌总价，包含全部显示范围" : "自己的挂牌或交易正在进行");
                return out;
            }
            InitialLandSettings settings = InitialLandSettings.get(server);
            out.addProperty("kind", "initial"); out.addProperty("revision", Long.toString(settings.revision));
            out.addProperty("total", settings.price.multiply(BigInteger.valueOf(selected.size())).toString());
            out.addProperty("currency", settings.currency); out.addProperty("currencyName", currencyName(settings.currency));
            scope(out, selected);
            String reason = !settings.enabled || !MarketConfig.get().serverLandSales() ? "管理员尚未启用无主地块出售" :
                    !MarketCurrencies.acceptsLand(settings.currency) ? "无主地块出售币种暂不可用" : LandBridge.initialGrantCheck(server, viewer, dimension, LandBridge.coordinates(selected));
            // A PAID or unresolved initial grant must not be sold a second time.
            for (Listing pending : MarketDesk.get(server).book().all()) {
                if (pending.initialGrant() && pending.chunks().stream().anyMatch(selected::contains)) reason = "所选范围有未完成交易，请等待恢复";
            }
            out.addProperty("buyable", "ok".equals(reason)); out.addProperty("reason", "ok".equals(reason) ? "无主地块整批总价" : reason);
            return out;
        } catch (RuntimeException error) { return denied("无法核验选区，请刷新或检查领地版本"); }
    }
    private static void scope(JsonObject out, List<ChunkRef> chunks) {
        JsonArray scope = new JsonArray(); for (ChunkRef c : chunks) { scope.add(c.x()); scope.add(c.z()); }
        out.add("scope", scope); out.addProperty("chunks", chunks.size());
    }
    private static String currencyName(String id) {
        return MarketCurrencies.landOptions().stream().filter(o -> o.id().equals(id)).map(MarketCurrencies.Option::label).findFirst().orElse(id);
    }
    public static String manage(MinecraftServer server, UUID viewer, String json) {
        JsonObject out = new JsonObject();
        var player = server == null || viewer == null ? null : server.getPlayerManager().getPlayer(viewer);
        if (server == null || !server.isOnThread() || player == null || !player.hasPermissionLevel(2)) {
            out.addProperty("ok", false); out.addProperty("reason", "需要管理员权限"); return out.toString();
        }
        try {
            if (json == null || json.length() > 8192) throw new IllegalArgumentException("请求过长");
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            String action = request.get("action").getAsString();
            InitialLandSettings settings = InitialLandSettings.get(server);
            if ("set".equals(action)) {
                if (!request.get("enabled").isJsonPrimitive() || !request.get("enabled").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("启用状态无效");
                String rawPrice = request.get("price").getAsString();
                if (!rawPrice.matches("[0-9]{1,30}")) throw new IllegalArgumentException("价格必须为正整数");
                BigInteger price = new BigInteger(rawPrice);
                if (price.signum() <= 0) throw new IllegalArgumentException("价格必须为正整数");
                String raw = request.get("currency").getAsString();
                var options = MarketCurrencies.landOptions();
                var matching = options.stream().filter(o -> o.id().equals(raw)).toList();
                if (matching.isEmpty()) matching = options.stream().filter(o -> o.label().equals(raw)).toList();
                if (matching.size() != 1) throw new IllegalArgumentException("币种不存在或中文名称重名，请使用币种ID");
                boolean enabled = request.get("enabled").getAsBoolean();
                boolean oldEnabled = settings.enabled; BigInteger oldPrice = settings.price; String oldCurrency = settings.currency; long oldRevision = settings.revision;
                settings.enabled = enabled; settings.price = price; settings.currency = matching.get(0).id(); settings.revision++;
                try { settings.save(server); }
                catch (RuntimeException saveError) { settings.enabled = oldEnabled; settings.price = oldPrice; settings.currency = oldCurrency; settings.revision = oldRevision; settings.markDirty(); throw saveError; }
            } else if (!"get".equals(action)) throw new IllegalArgumentException("未知管理操作");
            out.addProperty("ok", true); out.addProperty("reason", "只影响未来无主地块出售，不修改已有挂牌");
            out.addProperty("enabled", settings.enabled); out.addProperty("price", settings.price.toString());
            out.addProperty("currency", settings.currency); out.addProperty("currencyName", currencyName(settings.currency)); out.addProperty("revision", Long.toString(settings.revision));
            JsonArray currencies = new JsonArray(); for (var o : MarketCurrencies.landOptions()) { JsonObject row = new JsonObject(); row.addProperty("id", o.id()); row.addProperty("label", o.label()); currencies.add(row); } out.add("currencies", currencies);
        } catch (RuntimeException error) { out.addProperty("ok", false); out.addProperty("reason", "设置未保存：请检查价格、币种和请求格式"); }
        return out.toString();
    }
    public static void open(ServerPlayerEntity player, UUID parcelId, String action, String dimension, int[] coordinates) {
        if (player.getServer() == null || !player.getServer().isOnThread()) return;
        // Preserve the legacy empty-selection meaning: the entire known parcel.
        if (parcelId != null && coordinates != null && coordinates.length == 0) {
            var whole = LandBridge.parcel(player.getServer(), parcelId);
            if (whole != null) { coordinates = LandBridge.coordinates(whole.chunks()); dimension = whole.dimension(); }
        }
        if ("buy".equals(action)) {
            JsonObject quote = quoteObject(player.getServer(), player.getUuid(), dimension, coordinates);
            if (!quote.get("buyable").getAsBoolean()) { player.sendMessage(Text.literal(quote.get("reason").getAsString()), false); return; }
            if (LandUiChannel.open(player, "buy", parcelId, dimension, coordinates, quote)) return;
            if ("initial".equals(quote.get("kind").getAsString())) { InitialLandScreen.open(player, dimension, coordinates, quote); return; }
        }
        var land = parcelId == null ? null : LandBridge.parcel(player.getServer(), parcelId);
        if (land == null) { player.sendMessage(Text.literal("领地不存在，请刷新。"), false); return; }
        if (!action.equals("sell") && !action.equals("buy") && !action.equals("cancel")) return;
        if (!action.equals("buy") && !land.owner().equals(player.getUuid())) { player.sendMessage(Text.literal("只有所有者可以挂牌或撤销该领地。"), false); return; }
        List<ChunkRef> chunks;
        try { chunks = selection(dimension, coordinates); } catch (RuntimeException invalid) { return; }
        if (!land.chunks().containsAll(chunks)) { player.sendMessage(Text.literal("选区已变化，请刷新。"), false); return; }
        var listing = MarketDesk.get(player.getServer()).book().listingForParcel(parcelId);
        if (!action.equals("sell") && listing == null) { player.sendMessage(Text.literal("该领地没有市场挂牌。旧免费挂牌不会自动定价，请卖方重新挂牌。"), false); return; }
        if (action.equals("sell") && listing == null) {
            JsonObject form = new JsonObject(); form.addProperty("chunks", chunks.size());
            form.addProperty("currency", MarketConfig.get().currency()); form.addProperty("total", "1");
            if (LandUiChannel.open(player, "sell", parcelId, dimension, coordinates, form)) return;
        }
        MarketChestScreen.openLand(player, parcelId, chunks, action.equals("sell") && listing == null, listing == null ? null : listing.id());
    }
    static String confirmInitial(ServerPlayerEntity player, String dimension, int[] coordinates, JsonObject displayed) {
        MinecraftServer server = player.getServer();
        JsonObject fresh = quoteObject(server, player.getUuid(), dimension, coordinates);
        if (!fresh.get("buyable").getAsBoolean() || !"initial".equals(fresh.get("kind").getAsString())) return fresh.get("reason").getAsString();
        for (String key : List.of("revision", "total", "currency", "scope")) if (!fresh.get(key).equals(displayed.get(key))) return "价格或范围已变化，请重新打开确认";
        var data = MarketSavedData.get(server);
        Listing order = Listing.initialGrant(UUID.randomUUID(), new BigInteger(fresh.get("total").getAsString()), fresh.get("currency").getAsString(), dimension, selection(dimension, coordinates));
        if (!data.book().add(order)) return "无法建立交易";
        data.persistNow();
        var reply = MarketDesk.get(server).buy(player, order.id().toString(), order.customTotal());
        // Unpaid/refunded OPEN orders are not public listings. Reserved/PAID stay for recovery.
        Listing left = data.book().get(order.id()).orElse(null);
        if (left != null && left.status() == ListingStatus.OPEN) { data.book().remove(order.id()); data.persistNow(); }
        reply.lines().forEach(line -> player.sendMessage(line, false));
        return reply.ok() ? "土地购买成功" : "购买未完成，请查看交易提示";
    }
}
