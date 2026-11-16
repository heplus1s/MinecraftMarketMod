package dev.jdguo.market.ui;

import dev.jdguo.market.service.MarketDesk;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stable entry for a later chest menu. This class does not open a screen and
 * does not register a screen handler.
 * <p>
 * Attach it later like this:
 * <ol>
 * <li>Register the screen handler in that later build, not in this mod.</li>
 * <li>When the menu opens, call {@link #browse} and draw {@code views()}.</li>
 * <li>When the player confirms a button, call {@link #buy}, {@link #cancel},
 * {@link #listHeld}, {@link #listParcel}, {@link #listChunk}, or
 * {@link #listChunkRectangle}. Those methods are the same checkout the
 * commands use.</li>
 * <li>Redraw from the returned snapshot. Do not move items or savings in the
 * screen handler. A full inventory and a failed bank transfer are already
 * handled here.</li>
 * </ol>
 * The optional client screen belongs in MinecraftUIMod and should use the
 * {@code marketmod:hello} channel instead of this chest.
 */
public final class MarketChestUiHooks {
    private MarketChestUiHooks() {
    }

    public static MarketReply browse(ServerPlayerEntity viewer, String filter, String argument, int page) {
        return MarketDesk.get(viewer.getServer()).search(viewer, filter, argument, page);
    }

    public static MarketReply buy(ServerPlayerEntity player, String listingId) {
        return MarketDesk.get(player.getServer()).buy(player, listingId);
    }

    public static MarketReply cancel(ServerPlayerEntity player, String listingId) {
        return MarketDesk.get(player.getServer()).cancel(player, listingId, player.hasPermissionLevel(2));
    }

    public static MarketReply listHeld(ServerPlayerEntity player, boolean shopPrice, long customPrice, int count) {
        return MarketDesk.get(player.getServer()).listItem(player, shopPrice, customPrice, count);
    }

    public static MarketReply listHeld(ServerPlayerEntity player, boolean shopPrice, long customPrice, int count, String currency) {
        return MarketDesk.get(player.getServer()).listItem(player, shopPrice, customPrice, count, currency);
    }

    public static MarketReply listParcel(ServerPlayerEntity player, long price, boolean serverSale) {
        return MarketDesk.get(player.getServer()).listParcel(player, price, serverSale);
    }

    public static MarketReply listChunk(ServerPlayerEntity player, long price, boolean serverSale) {
        return MarketDesk.get(player.getServer()).listChunk(player, price, serverSale);
    }

    public static MarketReply listChunkRectangle(ServerPlayerEntity player, long price, boolean serverSale, int x1, int z1, int x2, int z2) {
        return MarketDesk.get(player.getServer()).listRectangle(player, price, serverSale, x1, z1, x2, z2);
    }

    public static MarketReply referencePrice(ServerPlayerEntity player) {
        return MarketDesk.get(player.getServer()).price(player);
    }
}
