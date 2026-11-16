package dev.jdguo.market.api;

import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.listing.ChunkRef;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.ui.MarketChestScreen;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.UUID;

/** Soft API used by land commands and map packets. Opening never debits or transfers. */
public final class LandMarketTerminal {
    private LandMarketTerminal() {}
    public static void open(ServerPlayerEntity player, UUID parcelId, String action, String dimension, int[] coordinates) {
        if (player.getServer() == null || !player.getServer().isOnThread()) return;
        var land = LandBridge.parcel(player.getServer(), parcelId);
        if (land == null) { player.sendMessage(Text.literal("领地不存在，请刷新。"), false); return; }
        if (!action.equals("sell") && !action.equals("buy") && !action.equals("cancel")) return;
        if (!action.equals("buy") && !land.owner().equals(player.getUuid())) {
            player.sendMessage(Text.literal("只有所有者可以挂牌或撤销该领地。"), false); return;
        }
        if (coordinates == null || coordinates.length % 2 != 0 || coordinates.length > 131072) return;
        var chunks = new ArrayList<ChunkRef>();
        for (int i = 0; i < coordinates.length; i += 2) chunks.add(new ChunkRef(dimension, coordinates[i], coordinates[i + 1]));
        if (!land.chunks().containsAll(chunks)) { player.sendMessage(Text.literal("选区已变化，请刷新。"), false); return; }
        var listing = MarketDesk.get(player.getServer()).book().listingForParcel(parcelId);
        if (!action.equals("sell") && listing == null) {
            player.sendMessage(Text.literal("该领地没有市场挂牌。旧免费挂牌不会自动定价，请卖方重新挂牌。"), false); return;
        }
        MarketChestScreen.openLand(player, parcelId, chunks, action.equals("sell") && listing == null,
                listing == null ? null : listing.id());
    }
}
