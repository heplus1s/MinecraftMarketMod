package dev.jdguo.market.api;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.integrate.BankBridge;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.listing.Checkout;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.ListingStatus;
import dev.jdguo.market.listing.Proceeds;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative administration of repurchases; plain player checkout stays separate. */
public final class GovernmentRepurchase {
    public static final UUID OWNER_ID = dev.jdguo.market.listing.ServerLandIdentity.ID;
    public static final String OWNER_NAME = dev.jdguo.market.listing.ServerLandIdentity.LABEL;
    private static final long TTL_NANOS = 60_000_000_000L;
    private static final Map<UUID, Snapshot> PREVIEWS = new HashMap<>();
    private GovernmentRepurchase() {}

    public record Snapshot(Listing listing, BigInteger total, UUID buyer, MinecraftServer server, long opened) {}

    public static Snapshot preview(ServerPlayerEntity player, String listingId) {
        MinecraftServer server = authorize(player);
        long now = System.nanoTime();
        PREVIEWS.values().removeIf(view -> view.server() != server || now - view.opened() >= TTL_NANOS);
        var found = MarketSavedData.get(server).book().find(listingId);
        if (found.listing() == null) throw new IllegalArgumentException(found.ambiguous() ? "挂牌编号不唯一" : "找不到该挂牌");
        UUID buyer = owner();
        validate(server, found.listing(), buyer);
        var snapshot = new Snapshot(found.listing(), found.listing().customTotal(), buyer, server, now);
        PREVIEWS.put(player.getUuid(), snapshot);
        return snapshot;
    }

    public static String confirm(ServerPlayerEntity player, Snapshot snapshot) {
        try {
            MinecraftServer server = authorize(player);
            if (snapshot == null || PREVIEWS.get(player.getUuid()) != snapshot)
                return "回购预览已失效，请重新打开";
            PREVIEWS.remove(player.getUuid());
            if (snapshot.server() != server || System.nanoTime() - snapshot.opened() >= TTL_NANOS)
                return "回购预览已过期，请重新打开";
            var data = MarketSavedData.get(server);
            Listing listing = data.book().get(snapshot.listing().id()).orElse(null);
            if (listing != snapshot.listing() || !snapshot.total().equals(listing.customTotal()) || !snapshot.buyer().equals(owner()))
                return "挂牌或金额已变化，请重新打开";
            validate(server, listing, snapshot.buyer());
            var outcome = Checkout.buyGovernment(data.book(), listing.id(), snapshot.buyer(), OWNER_NAME,
                    snapshot.total(), player.getUuid(), BankBridge.payments(), data::deliver, data::persistNow);
            if (!outcome.ok()) {
                if ("stale".equals(outcome.code()) && data.book().get(listing.id()).isEmpty()) LandBridge.clear(listing.parcelId());
                return explanation(outcome.code());
            }
            LandBridge.clear(listing.parcelId());
            MarketSync.broadcast(server);
            var seller = server.getPlayerManager().getPlayer(listing.seller());
            if (seller != null) seller.sendMessage(Text.literal("你的土地已由服务器回购，款项已入账。"), false);
            MarketMod.LOGGER.info("Land repurchase {} approved by {} ({})", listing.id(), player.getUuid(), snapshot.total());
            return "系统回购成功，已登记为" + OWNER_NAME + "，卖家款项已入账";
        } catch (IllegalArgumentException unavailable) {
            return unavailable.getMessage();
        } catch (RuntimeException | LinkageError uncertain) {
            MarketMod.LOGGER.error("System land repurchase requires state verification", uncertain);
            return "回购结果暂未确认，请先核对交易记录，不要重复提交";
        }
    }

    private static MinecraftServer authorize(ServerPlayerEntity player) {
        var server = player == null ? null : player.getServer();
        if (server == null || !server.isOnThread() || server.getPlayerManager().getPlayer(player.getUuid()) != player
                || !player.hasPermissionLevel(2)) throw new IllegalArgumentException("仅管理员可代办系统回购");
        return server;
    }

    private static UUID owner() {
        try {
            UUID id = LandBridge.governmentOwnerId();
            if (OWNER_ID.equals(id)) return id;
        } catch (IllegalStateException | LinkageError missing) { /* Pair with compatible land build. */ }
        throw new IllegalArgumentException("服务器领地模组尚不支持系统回购");
    }

    /** Listing-level eligibility shared by the picker and the authoritative preview. */
    public static boolean eligible(Listing listing) {
        return listing.status() == ListingStatus.OPEN && listing.kind() != ListingKind.ITEM
                && !listing.initialGrant() && listing.proceeds() == Proceeds.SELLER
                && !OWNER_ID.equals(listing.seller()) && "note".equals(listing.currency());
    }

    private static void validate(MinecraftServer server, Listing listing, UUID systemOwner) {
        if (listing.status() != ListingStatus.OPEN) throw new IllegalArgumentException("该挂牌正在交易，暂不能回购");
        if (!eligible(listing) || listing.seller().equals(systemOwner))
            throw new IllegalArgumentException("只支持回购玩家以央行券挂牌的土地");
        var parcel = LandBridge.parcel(server, listing.parcelId());
        if (parcel == null || !parcel.owner().equals(listing.seller()) || !parcel.chunks().containsAll(listing.chunks())
                || listing.kind() == ListingKind.PARCEL && parcel.chunks().size() != listing.chunks().size())
            throw new IllegalArgumentException("挂牌对应的土地状态已变化，请重新打开");
        var quote = BankBridge.quoteGovernmentBuyback(listing.seller(), listing.currency(), listing.customTotal());
        if (!quote.success()) throw new IllegalArgumentException(explanation(quote.code()));
    }

    private static String explanation(String code) {
        return switch (code) {
            case "insufficient_funds", "insufficient_available", "insufficient_system" -> "地产账户可用余额不足。请先拨款，再重新打开回购";
            case "bank_missing", "government_buyback_missing" -> "服务器银行模组尚不支持系统回购";
            case "bank_pending", "bank_failed", "delivery_pending", "refund_failed", "save_failed" -> "交易尚待核验或恢复，请不要重复提交";
            case "stale", "missing", "reserved" -> "该挂牌或土地已变化，请重新打开";
            default -> "系统回购未完成；请核对挂牌、地产账户或交易记录（" + code + "）";
        };
    }
}
