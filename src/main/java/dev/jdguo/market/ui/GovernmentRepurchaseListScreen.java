package dev.jdguo.market.ui;

import dev.jdguo.market.api.GovernmentRepurchase;
import dev.jdguo.market.message.MarketMessages;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lists eligible land orders directly from the server, without the client market's snapshot cap. */
public final class GovernmentRepurchaseListScreen extends GenericContainerScreenHandler {
    private static final int PAGE_SIZE = 36;
    private final ServerPlayerEntity viewer;
    private final SimpleInventory menu;
    private final List<UUID> shown = new ArrayList<>();
    private int page;

    private GovernmentRepurchaseListScreen(int id, PlayerInventory inventory, ServerPlayerEntity viewer) {
        this(id, inventory, viewer, new SimpleInventory(54));
    }

    private GovernmentRepurchaseListScreen(int id, PlayerInventory inventory, ServerPlayerEntity viewer,
                                           SimpleInventory menu) {
        super(ScreenHandlerType.GENERIC_9X6, id, inventory, menu, 6);
        this.viewer = viewer;
        this.menu = menu;
        renderPage();
    }

    public static void open(ServerPlayerEntity viewer) {
        if (!viewer.hasPermissionLevel(2)) {
            viewer.sendMessage(Text.literal("系统回购需要 OP 2 权限。"), false);
            return;
        }
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (id, inventory, player) -> new GovernmentRepurchaseListScreen(id, inventory, viewer),
                Text.literal("系统回购 · 玩家领地挂牌")));
    }

    private void renderPage() {
        menu.clear();
        shown.clear();
        var entries = MarketSavedData.get(viewer.getServer()).book().search(null, null, null, true)
                .stream().filter(GovernmentRepurchase::eligible).toList();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
            var listing = entries.get(i);
            shown.add(listing.id());
            var scope = LandScopeLabels.label(listing.kind().token(), listing.chunks().size());
            icon(i - start, Items.GRASS_BLOCK, scope + " · " + listing.parcelName(),
                    "卖方：" + listing.sellerName(), "挂牌编号：" + listing.shortId(),
                    "维度：" + listing.dimension(),
                    "总价：" + listing.customTotal() + " " + MarketMessages.currency(listing.currency()),
                    "点击预览实际区块和回购总价，不扣款");
        }
        if (entries.isEmpty()) icon(22, Items.PAPER, "暂无可回购挂牌", "仅显示玩家以央行券挂牌的领地");
        icon(45, Items.ARROW, "上一页");
        icon(46, Items.ARROW, "下一页");
        icon(49, Items.CLOCK, "刷新 · 第 " + (page + 1) + "/" + pages + " 页", "共 " + entries.size() + " 笔挂牌");
        icon(51, Items.BOOK, "回购说明", "由地产账户付款，过户给服务器用地", "不会扣管理员个人储蓄");
        icon(53, Items.BARRIER, "关闭");
        sendContentUpdates();
    }

    private void icon(int slot, Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        NbtList lines = new NbtList();
        for (String line : lore) lines.add(NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        stack.getOrCreateSubNbt("display").put("Lore", lines);
        menu.setStack(slot, stack);
    }

    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (viewer.currentScreenHandler != this || !canUse(player)
                || action != SlotActionType.PICKUP || button != 0 || slot < 0 || slot >= 54) return;
        if (slot == 53) {
            viewer.closeHandledScreen();
        } else if (slot < shown.size()) {
            GovernmentRepurchaseScreen.open(viewer, shown.get(slot).toString(), true);
        } else if (slot == 45 || slot == 46 || slot == 49) {
            if (slot == 45) page--;
            if (slot == 46) page++;
            renderPage();
        }
    }

    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) {
        return player == viewer && viewer.isAlive() && viewer.hasPermissionLevel(2);
    }
}
