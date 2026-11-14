package dev.jdguo.market.world;

import dev.jdguo.market.integrate.BankBridge;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.listing.Checkout;
import dev.jdguo.market.listing.Handoff;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.MarketBook;
import dev.jdguo.market.message.MarketMessages;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One market book for the whole server, stored on the overworld as {@code marketmod}.
 * Older files stay readable. Unknown keys are written back. Listings are not deleted on upgrade.
 */
public final class MarketSavedData extends PersistentState {
    public static final String ID = "marketmod";

    private final MinecraftServer server;
    private final MarketBook book;

    private MarketSavedData(MinecraftServer server, MarketBook book) {
        this.server = server;
        this.book = book;
    }

    public static MarketSavedData get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(
                nbt -> new MarketSavedData(server, MarketBook.read(nbt)),
                () -> new MarketSavedData(server, new MarketBook()),
                ID);
    }

    public MarketBook book() {
        return book;
    }

    public void recover() {
        Checkout.recover(book, BankBridge.payments(), this::deliver, this::persistNow);
    }

    public Handoff.Outcome deliver(Listing listing, UUID buyer, String buyerName) {
        if (listing.kind() == ListingKind.ITEM) {
            return ItemDelivery.deliver(server, buyer, listing.item());
        }
        return LandBridge.transfer(listing, buyer, buyerName);
    }

    public void deliverPending(ServerPlayerEntity player) {
        List<MarketBook.PendingItem> owned = book.pendingOf(player.getUuid());
        if (owned.isEmpty()) {
            return;
        }
        List<MarketBook.PendingItem> left = new ArrayList<>();
        boolean moved = false;
        boolean remain = false;
        for (MarketBook.PendingItem waiting : owned) {
            ItemStack stack = ItemDelivery.read(waiting.item());
            if (stack.isEmpty()) {
                left.add(waiting);
                remain = true;
                continue;
            }
            int before = stack.getCount();
            player.getInventory().insertStack(stack);
            if (stack.getCount() < before) {
                moved = true;
            }
            if (!stack.isEmpty()) {
                left.add(new MarketBook.PendingItem(player.getUuid(), stack.writeNbt(new NbtCompound()), waiting.createdAt()));
                remain = true;
            }
        }
        book.replacePending(player.getUuid(), left);
        persistNow();
        if (moved && remain) {
            player.sendMessage(MarketMessages.line("此前未能放入背包的物品已经交付一部分。剩余物品会在背包有空位后继续交付。"), false);
        } else if (moved) {
            player.sendMessage(MarketMessages.line("此前未能放入背包的物品已经交付。"), false);
        }
    }

    public void persistNow() {
        markDirty();
        try {
            server.getWorld(World.OVERWORLD).getPersistentStateManager().save();
        } catch (RuntimeException exception) {
            dev.jdguo.market.MarketMod.LOGGER.error("Could not flush market data", exception);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound written = book.write();
        for (String key : written.getKeys()) {
            nbt.put(key, written.get(key).copy());
        }
        return nbt;
    }
}
