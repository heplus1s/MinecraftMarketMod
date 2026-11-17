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

    public void recover() { recover(false); }

    public void recover(boolean landOnly) {
        Checkout.recover(book, BankBridge.payments(), this::deliver, this::persistNow, landOnly);
        // Initial purchase orders are private checkout journals, not reusable public offers.
        boolean cleaned = false;
        for (Listing listing : book.all()) {
            if (listing.initialGrant() && listing.status() == dev.jdguo.market.listing.ListingStatus.OPEN) {
                book.remove(listing.id()); cleaned = true;
            }
        }
        if (cleaned) persistNow();
    }

    public Handoff.Outcome deliver(Listing listing, UUID buyer, String buyerName) {
        if (listing.kind() == ListingKind.ITEM) {
            return ItemDelivery.deliver(server, buyer, listing.item());
        }
        return LandBridge.transfer(listing, buyer, buyerName);
    }

    public void deliverPending(ServerPlayerEntity player) {
        if (book.pendingDeliveryLocked(player.getUuid())) {
            player.sendMessage(MarketMessages.line("此前物品交付状态需要管理员核对，暂不重复发放。"), false);
            return;
        }
        List<MarketBook.PendingItem> owned = book.pendingOf(player.getUuid());
        if (owned.isEmpty()) {
            return;
        }
        book.lockPendingDelivery(player.getUuid());
        try {
            persistNow(); // Journal first. A crash at any later point must not repeat insertion.
        } catch (RuntimeException unavailable) {
            player.sendMessage(MarketMessages.line("待领箱暂时无法保存，未发放物品，请管理员核对存储状态。"), false);
            return; // Never let a mailbox I/O error reject an otherwise valid player login.
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
            ItemDelivery.insert(player, stack);
            if (stack.getCount() < before) {
                moved = true;
            }
            if (!stack.isEmpty()) {
                left.add(new MarketBook.PendingItem(player.getUuid(), ItemDelivery.write(stack), waiting.createdAt()));
                remain = true;
            }
        }
        try {
            DurablePlayerInventory.save(player);
            book.replacePending(player.getUuid(), left);
            book.unlockPendingDelivery(player.getUuid());
            persistNow();
        } catch (RuntimeException unknown) {
            if (!book.pendingDeliveryLocked(player.getUuid())) book.lockPendingDelivery(player.getUuid());
            player.sendMessage(MarketMessages.line("物品交付存档未确认，已锁定待核对，不会自动重复发放。"), false);
            return;
        }
        if (moved && remain) {
            player.sendMessage(MarketMessages.line("此前未能放入背包的物品已经交付一部分。剩余物品会在背包有空位后继续交付。"), false);
        } else if (moved) {
            player.sendMessage(MarketMessages.line("此前未能放入背包的物品已经交付。"), false);
        }
    }

    public void persistNow() {
        markDirty();
        try {
            DurableMarketState.save(server, ID, this);
        } catch (RuntimeException exception) {
            dev.jdguo.market.MarketMod.LOGGER.error("Could not flush market data", exception);
            throw exception;
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
