package dev.jdguo.market.world;

import dev.jdguo.market.listing.Handoff;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class ItemDelivery {
    private ItemDelivery() {
    }

    public static Handoff.Outcome deliver(MinecraftServer server, UUID buyer, NbtCompound item) {
        ItemStack stack = read(item);
        if (stack.isEmpty()) {
            return Handoff.Outcome.failure("bad_item");
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(buyer);
        if (player == null) {
            return Handoff.Outcome.success(write(stack));
        }
        insert(player, stack);
        DurablePlayerInventory.save(player);
        if (stack.isEmpty()) {
            return Handoff.Outcome.success(null);
        }
        return Handoff.Outcome.success(write(stack));
    }

    public static NbtCompound write(ItemStack stack) {
        ItemStack template = stack.copy(); template.setCount(1);
        NbtCompound tag = template.writeNbt(new NbtCompound());
        tag.putInt("MarketCount", stack.getCount());
        return tag;
    }
    /** Never pass oversized stacks to vanilla inventory insertion. */
    public static void insert(ServerPlayerEntity player, ItemStack remaining) {
        while (!remaining.isEmpty()) {
            ItemStack batch = remaining.copy(); batch.setCount(Math.min(remaining.getCount(), batch.getMaxCount()));
            int before = batch.getCount();
            player.getInventory().insertStack(batch);
            int moved = before - batch.getCount();
            remaining.decrement(moved);
            if (moved == 0 || !batch.isEmpty()) break;
        }
    }

    public static ItemStack read(NbtCompound item) {
        if (item == null || item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = ItemStack.fromNbt(item.copy());
            if (item.contains("MarketCount")) {
                int count = item.getInt("MarketCount");
                if (count <= 0 || count > 2304) return ItemStack.EMPTY;
                stack.setCount(count);
            }
            return stack;
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }
}
