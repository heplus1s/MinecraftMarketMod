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
            return Handoff.Outcome.success(stack.writeNbt(new NbtCompound()));
        }
        player.getInventory().insertStack(stack);
        DurablePlayerInventory.save(player);
        if (stack.isEmpty()) {
            return Handoff.Outcome.success(null);
        }
        return Handoff.Outcome.success(stack.writeNbt(new NbtCompound()));
    }

    public static ItemStack read(NbtCompound item) {
        if (item == null || item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.fromNbt(item.copy());
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }
}
