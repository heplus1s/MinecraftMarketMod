package dev.jdguo.market.ui;

import dev.jdguo.market.api.GovernmentRepurchase;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.message.MarketMessages;
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

/** Server-only confirmation. Preview, pagination and closing never submit a purchase. */
public final class GovernmentRepurchaseScreen extends GenericContainerScreenHandler {
    private final ServerPlayerEntity viewer;
    private final SimpleInventory menu;
    private final GovernmentRepurchase.Snapshot snapshot;
    private final Listing listing;
    private int page;
    private boolean consumed;
    private final boolean fromList;

    private GovernmentRepurchaseScreen(int id, PlayerInventory inventory, ServerPlayerEntity viewer,
                                       SimpleInventory menu, GovernmentRepurchase.Snapshot snapshot, boolean fromList) {
        super(ScreenHandlerType.GENERIC_9X6, id, inventory, menu, 6);
        this.viewer = viewer;
        this.menu = menu;
        this.snapshot = snapshot;
        this.listing = snapshot.listing();
        this.fromList = fromList;
        render();
    }

    public static void open(ServerPlayerEntity player, String listingId) {
        open(player, listingId, false);
    }

    static void open(ServerPlayerEntity player, String listingId, boolean fromList) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("系统代办回购需要 OP 2 权限。"), false);
            return;
        }
        if (dev.jdguo.market.net.GovernmentRepurchaseChannel.supports(player)) {
            dev.jdguo.market.net.GovernmentRepurchaseChannel.open(player, listingId);
            return;
        }
        try {
            var quote = GovernmentRepurchase.preview(player, listingId);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, who) ->
                    new GovernmentRepurchaseScreen(id, inv, player, new SimpleInventory(54), quote, fromList),
                    Text.literal("系统土地回购 · 确认前不扣款")));
        } catch (IllegalArgumentException failure) {
            player.sendMessage(Text.literal(failure.getMessage()), false);
        } catch (RuntimeException failure) {
            player.sendMessage(Text.literal("系统回购预览暂不可用，请检查服务端日志；未提交回购。"), false);
            dev.jdguo.market.MarketMod.LOGGER.error("Government repurchase preview failed", failure);
        }
    }

    private void icon(int slot, Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        NbtList lines = new NbtList();
        for (String line : lore) lines.add(NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        stack.getOrCreateSubNbt("display").put("Lore", lines);
        menu.setStack(slot, stack);
    }

    private void render() {
        menu.clear();
        icon(0, Items.PAPER, "挂牌 " + listing.shortId(), "完整编号 " + listing.id(),
                "宗地 " + listing.parcelName(), "宗地编号 " + listing.parcelId());
        icon(2, Items.PLAYER_HEAD, "收款卖方 " + listing.sellerName(), "UUID " + listing.seller());
        icon(4, Items.GOLD_INGOT, "回购总价 " + snapshot.total(),
                "币种 " + MarketMessages.currency(listing.currency()), "币种 ID " + listing.currency(),
                "付款账户：土地出让系统账户（land_grant）", "不扣管理员个人储蓄，不自动换汇");
        icon(6, Items.GRASS_BLOCK, LandScopeLabels.label(listing.kind().token(), listing.chunks().size()),
                "维度 " + listing.dimension(), "以下每格为本单实际回购的一个区块", "过户对象：服务器用地，不是操作管理员");
        icon(8, Items.BOOK, "预览中 · 尚未扣款", "只回购私人土地挂牌", "关闭或返回不会提交回购",
                "确认时重新核对挂牌、土地、权限和系统余额", "预览有效期 60 秒，过期请重新打开");
        int pages = Math.max(1, (listing.chunks().size() + 26) / 27);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * 27; i < Math.min(listing.chunks().size(), (page + 1) * 27); i++) {
            var chunk = listing.chunks().get(i);
            icon(9 + i - page * 27, Items.MAP, "区块 " + chunk.x() + ", " + chunk.z(),
                    "维度 " + chunk.dimension(),
                    "方块 X " + ((long) chunk.x() * 16) + " 至 " + ((long) chunk.x() * 16 + 15),
                    "方块 Z " + ((long) chunk.z() * 16) + " 至 " + ((long) chunk.z() * 16 + 15));
        }
        icon(45, Items.ARROW, "上一页范围");
        icon(46, Items.ARROW, "下一页范围");
        icon(47, Items.CLOCK, "范围第 " + (page + 1) + "/" + pages + " 页");
        icon(49, Items.EMERALD, "确认系统回购", "挂牌 " + listing.id(),
                "支付 " + snapshot.total() + " " + MarketMessages.currency(listing.currency()),
                "收款卖方 " + listing.sellerName(), "卖方 UUID " + listing.seller(),
                "从 land_grant 支付，以上全部区块过户服务器用地", "仅点击本按钮才提交；不能重复确认");
        icon(51, Items.ARROW, fromList ? "取消并返回回购列表" : "取消并返回市场");
        icon(53, Items.BARRIER, "取消并关闭");
        sendContentUpdates();
    }

    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (player != viewer || viewer.currentScreenHandler != this || consumed
                || action != SlotActionType.PICKUP || button != 0 || slot < 0 || slot >= 54) return;
        if (slot == 53 || slot == 51) {
            consumed = true;
            viewer.closeHandledScreen();
            if (slot == 51) {
                if (fromList) GovernmentRepurchaseListScreen.open(viewer);
                else MarketChestScreen.open(viewer);
            }
            return;
        }
        if (!viewer.hasPermissionLevel(2)) {
            consumed = true;
            viewer.closeHandledScreen();
            viewer.sendMessage(Text.literal("权限已变更，未提交系统回购。"), false);
            return;
        }
        if (slot == 49) {
            consumed = true; // Consume before calling settlement, even if settlement fails.
            viewer.closeHandledScreen();
            try {
                viewer.sendMessage(Text.literal(GovernmentRepurchase.confirm(viewer, snapshot)), false);
            } catch (IllegalArgumentException failure) {
                viewer.sendMessage(Text.literal(failure.getMessage()), false);
            } catch (RuntimeException failure) {
                viewer.sendMessage(Text.literal("回购处理异常，请先核对系统交易记录，不要重复提交。"), false);
                dev.jdguo.market.MarketMod.LOGGER.error("Government repurchase confirmation failed", failure);
            }
            return;
        }
        if (slot == 45) page--;
        if (slot == 46) page++;
        render();
    }

    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) {
        return player == viewer && viewer.isAlive() && viewer.hasPermissionLevel(2) && !consumed;
    }
}
