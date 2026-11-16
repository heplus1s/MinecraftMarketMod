package dev.jdguo.market.api;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Vanilla-safe explicit confirmation, with a one-shot guard against duplicate clicks. */
final class InitialLandScreen extends GenericContainerScreenHandler {
    private final ServerPlayerEntity viewer;
    private final String dimension;
    private final int[] coordinates;
    private final JsonObject quote;
    private boolean used;
    private InitialLandScreen(int syncId, PlayerInventory inventory, ServerPlayerEntity viewer, String dimension, int[] coordinates, JsonObject quote, SimpleInventory menu) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, inventory, menu, 3);
        this.viewer = viewer; this.dimension = dimension; this.coordinates = coordinates.clone(); this.quote = quote.deepCopy();
        icon(menu, 4, new ItemStack(Items.GRASS_BLOCK), "购买 " + quote.get("chunks").getAsInt() + " 区块 · " + dimension);
        icon(menu, 13, new ItemStack(Items.PAPER), "总价 " + quote.get("total").getAsString() + " " + quote.get("currencyName").getAsString());
        icon(menu, 11, new ItemStack(Items.EMERALD), "确认购买 · 银行储蓄扣款");
        icon(menu, 15, new ItemStack(Items.BARRIER), "取消 · 不扣款");
    }
    private static void icon(SimpleInventory menu, int slot, ItemStack stack, String label) { stack.setCustomName(Text.literal(label)); menu.setStack(slot, stack); }
    static void open(ServerPlayerEntity player, String dimension, int[] coordinates, JsonObject quote) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, who) -> new InitialLandScreen(id, inv, player, dimension, coordinates, quote, new SimpleInventory(27)), Text.literal("土地首售 · 确认购买")));
    }
    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (player != viewer || used || action != SlotActionType.PICKUP || button != 0) return;
        if (slot == 15) { used = true; viewer.closeHandledScreen(); return; }
        if (slot != 11) return;
        used = true;
        viewer.closeHandledScreen();
        try { viewer.sendMessage(Text.literal(LandMarketTerminal.confirmInitial(viewer, dimension, coordinates, quote)), false); }
        catch (RuntimeException error) { viewer.sendMessage(Text.literal("交易结果待核验，请勿重复购买；系统保留订单供恢复。"), false); }
    }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) { return player == viewer && viewer.isAlive(); }
}
