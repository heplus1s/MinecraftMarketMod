package dev.jdguo.market.ui;

import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.MarketCurrencies;
import dev.jdguo.market.integrate.ShopPrices;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.PriceMode;
import dev.jdguo.market.message.MarketMessages;
import dev.jdguo.market.service.MarketDesk;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Vanilla generic chest packets only: no custom client screen or client mod required. */
public final class MarketChestScreen extends GenericContainerScreenHandler {
    private final ServerPlayerEntity viewer;
    private final SimpleInventory menu;
    private final List<UUID> shown = new ArrayList<>();
    private UUID landTarget;
    private List<dev.jdguo.market.listing.ChunkRef> landChunks = List.of();
    private int page;
    private boolean mine;
    private boolean selling;
    private UUID selected;
    private BigInteger displayedTotal;
    private int sellKind; // 0 held item, 1 parcel, 2 current chunk
    private long price = 1;
    private String landRecommendation = "";
    private boolean landPriceConfirmedCurrency = true;
    private int count = 1;
    private int purchaseCount = 1;
    private String currency = MarketConfig.get().currency();

    private MarketChestScreen(int id, PlayerInventory inventory, ServerPlayerEntity viewer, SimpleInventory menu) {
        super(ScreenHandlerType.GENERIC_9X6, id, inventory, menu, 6);
        this.viewer = viewer;
        this.menu = menu;
        renderMenu();
    }

    public static void open(ServerPlayerEntity player) {
        if (dev.jdguo.market.net.MarketSync.open(player)) return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, who) ->
                new MarketChestScreen(id, inv, player, new SimpleInventory(54)), Text.literal("市场 · 挂牌交易")));
    }

    public static void openLand(ServerPlayerEntity player, UUID parcel, List<dev.jdguo.market.listing.ChunkRef> chunks, boolean sell, UUID listing) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, who) -> {
            MarketChestScreen screen = new MarketChestScreen(id, inv, player, new SimpleInventory(54));
            screen.landTarget = parcel;
            screen.landChunks = List.copyOf(chunks);
            screen.selling = sell;
            screen.sellKind = 1;
            screen.selected = listing;
            if (sell) {
                try {
                    var quote = dev.jdguo.market.api.LandPriceQuote.estimate(player.getServer(), chunks);
                    screen.price = new BigInteger(quote.get("total").getAsString()).longValueExact();
                    screen.currency = quote.get("currency").getAsString();
                    screen.landRecommendation = "推荐总价 " + screen.price + " " + MarketMessages.currency(screen.currency);
                } catch (RuntimeException unavailable) {
                    player.sendMessage(Text.literal("推荐价无法用于当前挂牌输入，请使用命令手动指定价格；未挂牌。"), false);
                    screen.selling = false;
                }
            }
            screen.renderMenu();
            return screen;
        }, Text.literal("土地市场 · 银行结算")));
    }

    private MarketDesk desk() { return MarketDesk.get(viewer.getServer()); }
    private void icon(int slot, Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        NbtList lines = new NbtList();
        for (String line : lore) lines.add(NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        stack.getOrCreateSubNbt("display").put("Lore", lines);
        menu.setStack(slot, stack);
    }
    private void renderMenu() {
        menu.clear(); shown.clear();
        if (selling) { renderSell(); sendContentUpdates(); return; }
        List<Listing> listings = desk().book().search(mine ? viewer.getUuid() : null, null, null, false);
        int pages = Math.max(1, (listings.size() + 35) / 36);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * 36; i < Math.min(listings.size(), (page + 1) * 36); i++) {
            Listing listing = listings.get(i); shown.add(listing.id());
            ListingView view = desk().view(listing, listing.priceMode() == PriceMode.SHOP
                    ? ShopPrices.quote(listing) : null);
            String title = listing.kind() == ListingKind.ITEM ? listing.itemId() + " × " + listing.count() : LandScopeLabels.label(listing.kind().token(), listing.chunks().size()) + " · " + view.parcelName();
            icon(i - page * 36, listing.kind() == ListingKind.ITEM ? Items.CHEST : Items.GRASS_BLOCK,
                    title, "编号 " + listing.shortId(), "卖方 " + listing.sellerName(),
                    "整单 " + (view.totalPrice().isBlank() ? "暂无报价" : view.totalPrice()) + " " + MarketMessages.currency(view.currency()),
                    listing.priceMode() == PriceMode.SHOP ? "浮动报价，成交时重新读取" : "固定价格", "点击查看并确认，尚不扣款");
        }
        Listing current = selected == null ? null : desk().book().get(selected).orElse(null);
        if (current != null) {
            ListingView view = desk().view(current, current.priceMode() == PriceMode.SHOP ? ShopPrices.quote(current) : null);
            purchaseCount = Math.min(Math.max(1,purchaseCount),current.count());
            displayedTotal = view.totalPrice().isBlank() ? null : current.kind() == ListingKind.ITEM
                    ? new BigInteger(view.unitPrice()).multiply(BigInteger.valueOf(purchaseCount)) : new BigInteger(view.totalPrice());
            if (current.kind() == ListingKind.ITEM) {
                icon(37, Items.REDSTONE, "买入数量 -1", "当前 " + purchaseCount);
                icon(39, Items.GLOWSTONE_DUST, "买入数量 +1", "当前 " + purchaseCount);
                icon(41, Items.CHEST, "买入数量 +64", "最多 " + current.count());
            }
            if (viewer.hasPermissionLevel(2) && current.kind() != ListingKind.ITEM
                    && current.proceeds() == dev.jdguo.market.listing.Proceeds.SELLER && !current.initialGrant()
                    && "note".equals(current.currency())
                    && current.status() == dev.jdguo.market.listing.ListingStatus.OPEN) {
                icon(43, Items.GOLD_BLOCK, "系统代办回购 · 预览", "从土地出让系统账户付款给卖方",
                        "回购后归服务器用地；不扣管理员个人余额", "点击仅预览，下一页单独确认");
            }
            icon(38, Items.PAPER, "已选 " + current.shortId(), "卖方 " + current.sellerName(), "整单 " + view.totalPrice() + " " + MarketMessages.currency(current.currency()), "选择购买 " + (current.kind() == ListingKind.ITEM ? purchaseCount + " 件" : LandScopeLabels.label(current.kind().token(), current.chunks().size())));
            if (current.seller().equals(viewer.getUuid())) icon(42, Items.BARRIER, "确认撤销自己的挂牌", "托管物品退回背包或待领取记录");
            else icon(40, Items.EMERALD, "确认购买 " + (current.kind() == ListingKind.ITEM ? purchaseCount + " 件" : LandScopeLabels.label(current.kind().token(), current.chunks().size())), "从银行扣除指定货币，不自动换汇", "币种 " + MarketMessages.currency(current.currency()), current.priceMode() == PriceMode.SHOP ? "浮动报价将在点击时重新读取" : "本次合计 " + displayedTotal);
        } else icon(38, Items.PAPER, "选择挂牌查看详情", "没有买单簿：本页为卖方挂牌，不是虚构的买卖深度");
        icon(45, Items.ARROW, "上一页"); icon(46, Items.ARROW, "下一页");
        icon(47, Items.PLAYER_HEAD, mine ? "我的挂牌 · 点击查看全部" : "全部挂牌 · 点击查看我的");
        icon(48, Items.CHEST, "挂牌手中物品", "打开数量、价格和结算币种选择");
        icon(49, Items.CLOCK, "刷新 · 第 " + (page + 1) + "/" + pages + " 页", "共 " + listings.size() + " 笔挂牌");
        icon(53, Items.BARRIER, "关闭"); sendContentUpdates();
    }
    private void renderSell() {
        List<MarketCurrencies.Option> options = MarketCurrencies.options();
        if (!options.isEmpty() && options.stream().noneMatch(o -> o.id().equals(currency))) currency = options.get(0).id();
        ItemStack hand = viewer.getMainHandStack();
        int available = 0;
        for (ItemStack stack : viewer.getInventory().main) if (ItemStack.canCombine(hand, stack)) available += stack.getCount();
        count = Math.max(1, Math.min(count, Math.max(1, available)));
        icon(4, Items.CHEST, hand.isEmpty() ? "请先把物品拿在主手" : hand.getName().getString(), "当前持有 " + available, "按主手物品类型从主库存托管同种同属性物品");
        icon(6, Items.GRASS_BLOCK, "挂牌类型 " + (sellKind == 0 ? "主手物品" : sellKind == 1 ? "脚下整宗土地" : "脚下区块"), "点击切换；土地价格为整单总价");
        if (landTarget != null) {
            icon(4, Items.GRASS_BLOCK, "目标宗地 " + landTarget, landChunks.isEmpty() ? "整宗地挂牌" : "选中 " + landChunks.size() + " 个区块");
            icon(6, Items.GRASS_BLOCK, "地图选区 · 已锁定", "最终确认时再次检查所有权和区块");
        }
        if (landTarget != null) icon(8, Items.BOOK, "推荐价（卖家可调整）", landRecommendation, "更换币种后请调整售价再确认");
        icon(11, Items.REDSTONE, "数量 -1"); icon(13, Items.PAPER, "挂牌数量 " + count); icon(15, Items.GLOWSTONE_DUST, "数量 +1");
        icon(20, Items.REDSTONE, "单价 -1"); icon(21, Items.REDSTONE, "单价 -10"); icon(22, Items.PAPER, "单价 " + price + " · 正整数"); icon(23, Items.GLOWSTONE_DUST, "单价 +10"); icon(24, Items.GLOWSTONE_DUST, "单价 +1");
        icon(29, Items.REDSTONE, "单价 -100"); icon(33, Items.GLOWSTONE_DUST, "单价 +100");
        icon(31, Items.GOLD_INGOT, "结算货币 " + MarketMessages.currency(currency), "ID: " + currency, "点击切换银行登记的储蓄货币", "个人币按挂牌币种从储蓄扣款");
        icon(40, Items.EMERALD, "确认挂牌", "数量 " + count + "，单价 " + price, "合计 " + BigInteger.valueOf(price).multiply(BigInteger.valueOf(sellKind == 0 ? count : 1)) + " " + MarketMessages.currency(currency), sellKind == 0 ? "托管物品，成交后从买方银行付款给你" : "锁定自己的土地挂牌，成交时过户并由银行付款");
        icon(45, Items.ARROW, "返回挂牌列表"); icon(53, Items.BARRIER, "关闭，不挂牌");
    }
    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        // Never call super: icons, hotbar swaps, drag, clone and quick-move must not move real items.
        if (player != viewer || action != SlotActionType.PICKUP || button != 0 || slot < 0 || slot >= 54) return;
        if (slot == 53) { viewer.closeHandledScreen(); return; }
        if (selling) {
            if (slot == 20 || slot == 21 || slot == 29 || slot == 24 || slot == 23 || slot == 33) landPriceConfirmedCurrency = true;
            switch (slot) {
                case 6 -> {
                    if (landTarget == null) {
                        sellKind = (sellKind + 1) % 3;
                        if (sellKind != 0) {
                            var pos = viewer.getChunkPos(); String dim = viewer.getWorld().getRegistryKey().getValue().toString();
                            var land = dev.jdguo.market.integrate.LandBridge.at(viewer.getServer(), dim, pos.x, pos.z);
                            if (land == null || !land.owner().equals(viewer.getUuid())) { viewer.sendMessage(Text.literal("请站在自己的领地内再打开出售。"), false); sellKind = 0; }
                            else {
                                dev.jdguo.market.api.LandMarketTerminal.open(viewer, land.id(), "sell", dim,
                                        sellKind == 1 ? dev.jdguo.market.integrate.LandBridge.coordinates(land.chunks()) : new int[]{pos.x,pos.z}); return;
                            }
                        }
                    }
                }
                case 11 -> count = Math.max(1, count - 1);
                case 15 -> count = Math.min(9999, count + 1);
                case 20 -> price = Math.max(1, price - 1);
                case 21 -> price = Math.max(1, price - 10);
                case 29 -> price = Math.max(1, price - 100);
                case 24 -> price = price == Long.MAX_VALUE ? price : price + 1;
                case 23 -> price = price > Long.MAX_VALUE - 10 ? Long.MAX_VALUE : price + 10;
                case 33 -> price = price > Long.MAX_VALUE - 100 ? Long.MAX_VALUE : price + 100;
                case 31 -> {
                    if (landTarget != null) landPriceConfirmedCurrency = false;
                    var options = MarketCurrencies.options();
                    for (int i = 0; i < options.size(); i++) if (options.get(i).id().equals(currency)) { currency = options.get((i + 1) % options.size()).id(); break; }
                }
                case 40 -> {
                    if (landTarget != null && !landPriceConfirmedCurrency) { viewer.sendMessage(Text.literal("切换币种后请先调整售价，推荐价不会自动换算。"), false); break; }
                    MarketReply reply = landTarget != null ? desk().listSelectedLand(viewer, landTarget, landChunks, price, currency) : switch (sellKind) {
                        case 1 -> desk().listParcel(viewer, price, false, currency);
                        case 2 -> desk().listChunk(viewer, price, false, currency);
                        default -> desk().listItem(viewer, false, price, count, currency);
                    };
                    reply.lines().forEach(line -> viewer.sendMessage(line, false));
                    if (reply.ok()) selling = false;
                }
                case 45 -> selling = false;
                default -> { }
            }
        } else if (slot < shown.size()) { selected = shown.get(slot); purchaseCount = 1; }
        else switch (slot) {
            case 37 -> purchaseCount = Math.max(1, purchaseCount-1);
            case 39 -> purchaseCount++;
            case 41 -> purchaseCount += 64;
            case 45 -> { page--; selected = null; }
            case 46 -> { page++; selected = null; }
            case 47 -> { mine = !mine; page = 0; selected = null; }
            case 48 -> { landTarget = null; landChunks = List.of(); sellKind = 0; selling = true; count = 1; }
            case 43 -> {
                if (selected != null && viewer.hasPermissionLevel(2)) {
                    GovernmentRepurchaseScreen.open(viewer, selected.toString());
                    return;
                }
            }
            case 40, 42 -> {
                if (selected != null) {
                    MarketReply reply = slot == 40 ? (displayedTotal == null ? MarketReply.message(false, "shop_missing", MarketMessages.line("暂无有效报价，请刷新。")) : desk().buy(viewer, selected.toString(), displayedTotal, desk().book().get(selected).map(l -> l.kind() == ListingKind.ITEM ? purchaseCount : 0).orElse(0))) : desk().cancel(viewer, selected.toString(), false);
                    reply.lines().forEach(line -> viewer.sendMessage(line, false));
                    selected = null;
                }
            }
            default -> { }
        }
        renderMenu();
    }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canUse(PlayerEntity player) { return player == viewer && viewer.isAlive(); }
}
