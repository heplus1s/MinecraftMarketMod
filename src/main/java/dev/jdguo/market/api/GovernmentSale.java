package dev.jdguo.market.api;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.BankBridge;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.integrate.LandView;
import dev.jdguo.market.listing.ChunkRef;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.ListingStatus;
import dev.jdguo.market.listing.Proceeds;
import dev.jdguo.market.listing.ServerLandIdentity;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.ui.MarketChestScreen;
import dev.jdguo.market.ui.MarketReply;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** Admin acts on behalf of the reserved owner, never switches the player's identity or account. */
public final class GovernmentSale {
    private GovernmentSale() {}

    public static void openStanding(ServerPlayerEntity player) {
        try {
            MinecraftServer server = authorize(player);
            String dimension = player.getWorld().getRegistryKey().getValue().toString();
            var land = LandBridge.at(server, dimension, player.getBlockX() >> 4, player.getBlockZ() >> 4);
            if (land == null || !ServerLandIdentity.ID.equals(land.owner()))
                throw new IllegalArgumentException("请站在服务器用地内，或在领地管理选择服务器宗地。");
            open(player, land.id());
        } catch (IllegalArgumentException denied) {
            player.sendMessage(Text.literal(denied.getMessage()), false);
        } catch (RuntimeException | LinkageError unavailable) {
            player.sendMessage(Text.literal("无法核验服务器用地，请检查领地及市场版本。"), false);
        }
    }

    public static void open(ServerPlayerEntity player, UUID parcelId) {
        try {
            MinecraftServer server = authorize(player);
            LandView land = scope(server, player, parcelId);
            Listing listing = MarketSavedData.get(server).book().listingForParcel(parcelId);
            if (listing != null && !ServerLandIdentity.isResale(listing.proceeds(), listing.seller()))
                throw new IllegalArgumentException("该宗地已有其他交易，不能代办。");
            MarketChestScreen.openServerLand(player, land, listing == null ? null : listing.id());
        } catch (IllegalArgumentException denied) {
            player.sendMessage(Text.literal(denied.getMessage()), false);
        } catch (RuntimeException | LinkageError unavailable) {
            player.sendMessage(Text.literal("无法打开服务器用地管理，请检查领地及市场版本。"), false);
        }
    }

    public static MarketReply list(ServerPlayerEntity player, UUID parcelId, List<ChunkRef> displayed, long price) {
        try {
            MinecraftServer server = authorize(player);
            if (price <= 0) return result(false, "price", "请输入大于零的挂牌总价。");
            if (!MarketConfig.get().serverLandSales()) return result(false, "server_disabled", "服务器出让已在市场配置中关闭。");
            if (!BankBridge.governmentResaleAvailable())
                return result(false, "bank_missing", "银行模组尚不支持服务器用地结算。");
            LandView land = scope(server, player, parcelId);
            if (displayed == null || displayed.size() != land.chunks().size() || !land.same(displayed))
                return result(false, "stale", "宗地范围已变化，请重开挂牌预览。");
            if (land.chunks().isEmpty() || land.chunks().size() > MarketConfig.get().maxChunksPerListing())
                return result(false, "too_many_chunks", "宗地范围超出市场单笔挂牌上限，请先拆分。");
            var data = MarketSavedData.get(server);
            var listing = Listing.land(UUID.randomUUID(), ListingKind.PARCEL, ServerLandIdentity.ID, ServerLandIdentity.LABEL,
                    Proceeds.LAND_GRANT, BigInteger.valueOf(price), "note", parcelId, land.name(), land.dimension(), land.chunks());
            if (!data.book().add(listing)) return result(false, "listed", "该宗地已有挂牌或未完成交易。");
            data.persistNow();
            LandBridge.bind(parcelId, listing.id());
            MarketSync.broadcast(server);
            MarketMod.LOGGER.info("Server land listing {} created by {}", listing.id(), player.getUuid());
            return result(true, "listed", "服务器用地已挂牌 " + listing.shortId() + "；成交款进入地产账户。");
        } catch (IllegalArgumentException denied) {
            return result(false, "permission", denied.getMessage());
        } catch (RuntimeException | LinkageError unavailable) {
            MarketMod.LOGGER.warn("Server land listing requires verification", unavailable);
            return result(false, "pending", "未能确认挂牌结果，请刷新市场核对，不要重复提交。");
        }
    }

    public static MarketReply cancel(ServerPlayerEntity player, UUID listingId) {
        try {
            MinecraftServer server = authorize(player);
            var desk = MarketDesk.get(server);
            var listing = desk.book().get(listingId).orElse(null);
            if (listing == null || listing.status() != ListingStatus.OPEN || listing.kind() == ListingKind.ITEM
                    || !ServerLandIdentity.isResale(listing.proceeds(), listing.seller()) || listing.initialGrant())
                return result(false, "stale", "该挂牌不属于服务器用地，或交易已在处理。");
            scope(server, player, listing.parcelId());
            return desk.cancel(player, listingId.toString(), true);
        } catch (IllegalArgumentException denied) {
            return result(false, "permission", denied.getMessage());
        } catch (RuntimeException | LinkageError unavailable) {
            return result(false, "pending", "撤销结果待核验，请刷新市场。");
        }
    }

    private static LandView scope(MinecraftServer server, ServerPlayerEntity player, UUID parcelId) {
        if (parcelId == null) throw new IllegalArgumentException("没有选中服务器宗地。");
        LandView scope = LandBridge.governmentSaleScope(server, player.getUuid(), parcelId);
        if (scope == null || !ServerLandIdentity.ID.equals(scope.owner()))
            throw new IllegalArgumentException("只能以服务器名义处理服务器用地。");
        return scope;
    }

    private static MinecraftServer authorize(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isOnThread() || server.getPlayerManager().getPlayer(player.getUuid()) != player
                || !player.hasPermissionLevel(2)) throw new IllegalArgumentException("只有管理员可以管理服务器用地。");
        return server;
    }

    private static MarketReply result(boolean ok, String code, String message) {
        return MarketReply.message(ok, code, Text.literal(message));
    }
}
