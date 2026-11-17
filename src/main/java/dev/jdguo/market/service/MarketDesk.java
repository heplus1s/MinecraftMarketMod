package dev.jdguo.market.service;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.BankBridge;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.integrate.LandView;
import dev.jdguo.market.integrate.ShopPrices;
import dev.jdguo.market.listing.Checkout;
import dev.jdguo.market.listing.ChunkRef;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.MarketBook;
import dev.jdguo.market.listing.PriceMode;
import dev.jdguo.market.listing.Proceeds;
import dev.jdguo.market.message.MarketMessages;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.ui.ListingView;
import dev.jdguo.market.ui.MarketReply;
import dev.jdguo.market.world.ItemDelivery;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one path commands, the optional UI channel, and a later chest menu all use.
 */
public final class MarketDesk {
    private final MinecraftServer server;
    private final MarketSavedData data;

    private MarketDesk(MinecraftServer server, MarketSavedData data) {
        this.server = server;
        this.data = data;
    }

    public static MarketDesk get(MinecraftServer server) {
        return new MarketDesk(server, MarketSavedData.get(server));
    }

    public MarketBook book() {
        return data.book();
    }

    public MarketReply listItem(ServerPlayerEntity player, boolean shop, long price, int requested) {
        return listItem(player, shop, price, requested, MarketConfig.get().currency());
    }

    public MarketReply listItem(ServerPlayerEntity player, boolean shop, long price, int requested, String currency) {
        return listItem(player, shop, price, requested, currency, 100);
    }
    public MarketReply listItem(ServerPlayerEntity player, boolean shop, long price, int requested, String currency, int percent) {
        if (percent < 1 || percent > 1000) return fail("price");
        currency = dev.jdguo.market.integrate.MarketCurrencies.resolve(currency);
        if (currency == null) return fail("unknown_currency");
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            return fail("empty_hand");
        }
        int count = requested <= 0 ? hand.getCount() : requested;
        int available = 0;
        for (ItemStack stack : player.getInventory().main) if (ItemStack.canCombine(hand, stack)) available += stack.getCount();
        if (count <= 0 || count > available || count > 2304) {
            return fail("bad_count");
        }
        if (!shop && price <= 0) {
            return fail("price");
        }
        if (book().countOf(player.getUuid()) >= MarketConfig.get().maxListingsPerPlayer()) {
            return fail("limit");
        }
        String itemId = net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).toString();
        BigInteger quote = shop ? ShopPrices.quote(itemId, currency) : null;
        ItemStack listed = hand.copy();
        listed.setCount(1);
        Listing listing = Listing.item(UUID.randomUUID(), player.getUuid(), player.getGameProfile().getName(),
                Proceeds.SELLER, shop ? PriceMode.SHOP : PriceMode.CUSTOM,
                shop ? BigInteger.ZERO : BigInteger.valueOf(price), currency, count, itemId,
                listed.writeNbt(new NbtCompound()));
        if (shop) { listing.shopPercent(percent); quote = ShopPrices.quote(listing); }
        if (!book().add(listing)) {
            return fail("limit");
        }
        // Persist a non-purchasable escrow intent BEFORE touching the player's inventory.
        // Interrupted intents remain RESERVED; neither restart nor cancel may duplicate them.
        book().lockItem(listing.id());
        data.persistNow();
        int remaining = count;
        for (ItemStack stack : player.getInventory().main) {
            if (remaining == 0) break;
            if (!ItemStack.canCombine(listed, stack)) continue;
            int take = Math.min(remaining, stack.getCount()); stack.decrement(take); remaining -= take;
        }
        try {
            dev.jdguo.market.world.DurablePlayerInventory.save(player);
            book().unlockItem(listing.id());
            data.persistNow();
        } catch (RuntimeException uncertain) {
            if (listing.status() == dev.jdguo.market.listing.ListingStatus.OPEN) book().lockItem(listing.id());
            return fail("inventory_pending");
        }
        MarketSync.broadcast(server);
        Text name = listed.getName().copy();
        String priceText = shop
                ? "价格按商店参考价的 " + percent + "% 浮动（单价向上取整）"
                : "自定义价格每件 " + money(BigInteger.valueOf(price), currency) + "，合计 " + money(listing.customTotal(), currency);
        MutableText line = MarketMessages.line("已挂牌 " + listing.shortId() + "。", name,
                " × " + count + "，" + priceText + "。支持按数量部分成交。货款交给卖方，不进入中央银行。");
        if (shop && quote == null) {
            return changed("listed", List.of(line, MarketMessages.line("商店目前没有给出参考价格。如果购买时仍然没有价格，交易不会成交。")), List.of(view(listing, null)));
        }
        return changed("listed", List.of(line), List.of(view(listing, quote)));
    }

    public MarketReply listParcel(ServerPlayerEntity player, long price, boolean serverSale) {
        return listParcel(player, price, serverSale, MarketConfig.get().currency());
    }
    public MarketReply listParcel(ServerPlayerEntity player, long price, boolean serverSale, String currency) {
        currency = dev.jdguo.market.integrate.MarketCurrencies.resolve(currency);
        if (currency == null) return fail("unknown_currency");
        if (price <= 0) {
            return fail("price");
        }
        MarketReply gate = landReady(player, serverSale);
        if (gate != null) {
            return gate;
        }
        LandView land = LandBridge.at(server, dimension(player), player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (land == null) {
            return fail("not_claimed");
        }
        return listLand(player, land, land.chunks(), price, serverSale, true, currency);
    }

    /** Explicit map selection, revalidated at confirmation rather than using the player's current position. */
    public MarketReply listSelectedLand(ServerPlayerEntity player, UUID parcelId, List<ChunkRef> selected,
                                       long price, String currency) {
        currency = dev.jdguo.market.integrate.MarketCurrencies.resolve(currency);
        if (currency == null) return fail("unknown_currency");
        if (price <= 0) return fail("price");
        MarketReply gate = landReady(player, false);
        if (gate != null) return gate;
        LandView land = LandBridge.parcel(server, parcelId);
        if (land == null) return fail("not_claimed");
        List<ChunkRef> chunks;
        try {
            chunks = dev.jdguo.market.listing.LandSelection.validate(land.chunks(), selected, MarketConfig.get().maxChunksPerListing());
        } catch (IllegalArgumentException invalid) { return fail("stale"); }
        return listLand(player, land, chunks, price, false, land.same(chunks), currency);
    }

    public MarketReply listChunk(ServerPlayerEntity player, long price, boolean serverSale) {
        return listChunk(player, price, serverSale, MarketConfig.get().currency());
    }
    public MarketReply listChunk(ServerPlayerEntity player, long price, boolean serverSale, String currency) {
        int x = player.getBlockX() >> 4;
        int z = player.getBlockZ() >> 4;
        return listRectangle(player, price, serverSale, x, z, x, z, currency);
    }

    public MarketReply listRectangle(ServerPlayerEntity player, long price, boolean serverSale, int x1, int z1, int x2, int z2) {
        return listRectangle(player, price, serverSale, x1, z1, x2, z2, MarketConfig.get().currency());
    }
    public MarketReply listRectangle(ServerPlayerEntity player, long price, boolean serverSale, int x1, int z1, int x2, int z2, String currency) {
        currency = dev.jdguo.market.integrate.MarketCurrencies.resolve(currency);
        if (currency == null) return fail("unknown_currency");
        if (price <= 0) {
            return fail("price");
        }
        MarketReply gate = landReady(player, serverSale);
        if (gate != null) {
            return gate;
        }
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        long width = (long) maxX - minX + 1;
        long depth = (long) maxZ - minZ + 1;
        if (width <= 0 || depth <= 0 || width > Integer.MAX_VALUE || depth > Integer.MAX_VALUE
                || width * depth > MarketConfig.get().maxChunksPerListing()) {
            return fail("too_many_chunks");
        }
        String dimension = dimension(player);
        List<ChunkRef> selected = new ArrayList<>();
        LandView parcel = null;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LandView here = LandBridge.at(server, dimension, x, z);
                if (here == null) {
                    return fail("no_chunks");
                }
                if (!here.owner().equals(player.getUuid())) {
                    return fail("not_owner");
                }
                if (parcel == null) {
                    parcel = here;
                } else if (!parcel.id().equals(here.id())) {
                    return fail("mixed");
                }
                selected.add(new ChunkRef(dimension, x, z));
            }
        }
        boolean whole = parcel.same(selected);
        return listLand(player, parcel, whole ? parcel.chunks() : selected, price, serverSale, whole, currency);
    }

    public MarketReply buy(ServerPlayerEntity player, String id) {
        return buy(player, id, null);
    }
    public MarketReply buy(ServerPlayerEntity player, String id, BigInteger maximumTotal) {
        return buy(player, id, maximumTotal, 0);
    }
    public MarketReply buy(ServerPlayerEntity player, String id, BigInteger maximumTotal, int requested) {
        MarketBook.Lookup lookup = book().find(id);
        if (lookup.listing() == null) {
            return fail(lookup.ambiguous() ? "ambiguous" : "missing");
        }
        Listing listing = lookup.listing();
        if (listing.kind() != ListingKind.ITEM && !LandBridge.available()) {
            return fail("land_missing");
        }
        if (requested < 0 || (requested > 0 && (listing.kind() != ListingKind.ITEM || requested > listing.count()))) return fail("bad_count");
        if (listing.status() != dev.jdguo.market.listing.ListingStatus.OPEN) return fail("reserved");
        if (player.getUuid().equals(listing.seller())) return fail("self");
        int quantity = requested > 0 ? requested : listing.count();
        BigInteger amount;
        if (listing.priceMode() == PriceMode.SHOP) {
            BigInteger unit = ShopPrices.quote(listing);
            if (unit == null) {
                return fail("shop_missing");
            }
            amount = unit.multiply(BigInteger.valueOf(quantity));
        } else {
            amount = listing.kind() == ListingKind.ITEM ? listing.unitPrice().multiply(BigInteger.valueOf(quantity)) : listing.customTotal();
        }
        if (!withinMaximum(amount, maximumTotal)) {
            return fail("price_moved");
        }
        UUID originalId = listing.id();
        if (listing.kind() == ListingKind.ITEM && quantity < listing.count()) {
            listing = book().splitItem(listing.id(), quantity);
            book().lockItem(originalId); // Unknown child outcome also freezes the remaining escrow.
            data.persistNow(); // Persist both remaining offer and split escrow before any payment.
        }
        Checkout.Outcome outcome = Checkout.buy(book(), listing.id(), player.getUuid(), player.getGameProfile().getName(),
                amount, BankBridge.payments(), data::deliver, data::persistNow);
        if (!outcome.ok()) {
            if (!originalId.equals(listing.id()) && listing.status() == dev.jdguo.market.listing.ListingStatus.OPEN
                    && book().get(listing.id()).isPresent()) {
                book().unlockItem(originalId); book().restoreSplit(originalId, listing.id()); data.persistNow();
            }
            if ("stale".equals(outcome.code()) && listing.parcelId() != null) {
                LandBridge.clear(listing.parcelId());
            }
            return fail(outcome.code());
        }
        if (!originalId.equals(listing.id())) { book().unlockItem(originalId); data.persistNow(); }
        if (listing.parcelId() != null) {
            LandBridge.clear(listing.parcelId());
        }
        tellSeller(listing, amount);
        MarketSync.broadcast(server);
        return MarketReply.message(true, "bought", boughtLine(listing, amount, outcome.pendingDelivery()));
    }

    public static boolean withinMaximum(BigInteger amount, BigInteger maximum) {
        return dev.jdguo.market.listing.ListingTerms.withinMaximum(amount, maximum);
    }

    public MarketReply cancel(ServerPlayerEntity player, String id, boolean admin) {
        MarketBook.Lookup lookup = book().find(id);
        if (lookup.listing() == null) {
            return fail(lookup.ambiguous() ? "ambiguous" : "missing");
        }
        Listing listing = lookup.listing();
        if (listing.status() != dev.jdguo.market.listing.ListingStatus.OPEN) {
            return fail("reserved");
        }
        boolean owner = listing.seller().equals(player.getUuid());
        if (!owner && !admin) {
            return fail("not_seller");
        }
        if (listing.kind() == ListingKind.ITEM) {
            ItemStack stack = ItemDelivery.read(listing.item());
            if (stack.isEmpty()) {
                return fail("bad_item");
            }
            // Cancellation is a single market-file move into the mailbox. Inventory delivery
            // happens through its separately journaled path, never before this checkpoint.
            book().lockItem(listing.id());
            data.persistNow();
            book().remove(listing.id());
            book().addPending(listing.seller(), ItemDelivery.write(stack));
            data.persistNow();
            ServerPlayerEntity seller = server.getPlayerManager().getPlayer(listing.seller());
            if (seller != null) data.deliverPending(seller);
            boolean pending = !book().pendingOf(listing.seller()).isEmpty();
            MarketSync.broadcast(server);
            String body = pending
                    ? "已撤销挂牌 " + listing.shortId() + "。背包空间不足，物品会在卖方下次进入世界时退回。"
                    : "已撤销挂牌 " + listing.shortId() + "。物品已退回卖方背包。";
            return MarketReply.message(true, "cancelled", MarketMessages.line(body));
        }
        book().remove(listing.id());
        LandBridge.clear(listing.parcelId());
        data.persistNow();
        MarketSync.broadcast(server);
        return MarketReply.message(true, "cancelled", MarketMessages.line("已撤销挂牌 " + listing.shortId() + "。宗地仍归持有人所有。"));
    }

    public MarketReply search(ServerPlayerEntity viewer, String filter, String argument, int page) {
        UUID seller = null;
        String sellerName = null;
        String itemId = null;
        boolean landOnly = false;
        switch (filter == null ? "all" : filter) {
            case "mine" -> seller = viewer == null ? new UUID(0L, 0L) : viewer.getUuid();
            case "player" -> sellerName = argument == null ? "" : argument;
            case "item" -> itemId = argument == null ? "" : argument;
            case "land" -> landOnly = true;
            default -> {
            }
        }
        List<Listing> found = book().search(seller, sellerName, itemId, landOnly);
        if (found.isEmpty()) {
            return MarketReply.message(true, "empty", MarketMessages.line("现在没有符合条件的挂牌。"));
        }
        int size = MarketConfig.get().pageSize();
        int pages = (found.size() + size - 1) / size;
        int safePage = Math.max(1, page);
        int from = (safePage - 1) * size;
        if (from >= found.size()) {
            return MarketReply.message(true, "empty", MarketMessages.line("没有更多挂牌。当前共 " + pages + " 页。"));
        }
        int to = Math.min(found.size(), from + size);
        List<Text> lines = new ArrayList<>();
        List<ListingView> views = new ArrayList<>();
        lines.add(MarketMessages.line("第 " + safePage + " 页，共 " + pages + " 页。"));
        for (Listing listing : found.subList(from, to)) {
            views.add(view(listing, shopUnit(listing)));
            lines.add(summary(listing));
        }
        return MarketReply.of(true, "search", lines, views);
    }

    public MarketReply info(ServerPlayerEntity player, String id) {
        MarketBook.Lookup lookup = book().find(id);
        if (lookup.listing() == null) {
            return fail(lookup.ambiguous() ? "ambiguous" : "missing");
        }
        Listing listing = lookup.listing();
        List<Text> lines = new ArrayList<>();
        lines.add(summary(listing));
        if (listing.kind() == ListingKind.ITEM) {
            lines.add(MarketMessages.line("编号 " + listing.id() + "。物品支持按数量部分成交，领地整笔成交。"));
            var bid = ShopPrices.exactQuote(listing.itemId(), listing.currency(), false);
            var ask = ShopPrices.exactQuote(listing.itemId(), listing.currency(), true);
            if (bid != null && ask != null) {
                lines.add(MarketMessages.line("商店每件收购 " + bid.toPlainString() + "，出售 "
                        + ask.toPlainString() + " " + MarketMessages.currency(listing.currency()) + "。"));
            }
        } else {
            lines.add(MarketMessages.line(MarketMessages.dimension(listing.dimension()) + "，共 " + listing.chunks().size()
                    + " 个区块。最小单位是原版 16×16 区块。编号 " + listing.id() + "。"));
            if (listing.proceeds() == Proceeds.LAND_GRANT) {
                lines.add(MarketMessages.line("这是服务器出让。货款进入土地出让账户，不进入持有人的储蓄账户。"));
            }
        }
        return MarketReply.of(true, "info", lines, List.of(view(listing, shopUnit(listing))));
    }

    public MarketReply price(ServerPlayerEntity player) {
        ItemStack hand = player.getMainHandStack();
        if (hand.isEmpty()) {
            return fail("empty_hand");
        }
        String itemId = net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).toString();
        String currency = MarketConfig.get().currency();
        BigInteger quote = ShopPrices.quote(itemId, currency);
        Text name = hand.getName().copy();
        if (quote == null) {
            return MarketReply.message(true, "shop_missing", MarketMessages.line("商店目前没有", name, "的参考价格。你仍然可以用自定义价格挂牌。"));
        }
        return MarketReply.message(true, "price", MarketMessages.line("商店参考价格：", name, "每件 " + money(quote, currency) + "。已挂出的商店参考价格在购买时再读取一次。"));
    }

    public MarketReply reload() {
        MarketConfig.load(MarketMod.configPath());
        return MarketReply.message(true, "reload", MarketMessages.line("已经重新读取市场配置。新的挂牌使用"
                + MarketMessages.currency(MarketConfig.get().currency()) + "结算。已经挂出的价格和币种不变。"));
    }

    private MarketReply listLand(ServerPlayerEntity player, LandView land, List<ChunkRef> chunks, long price, boolean serverSale, boolean whole, String currency) {
        if (!land.owner().equals(player.getUuid())) {
            return fail("not_owner");
        }
        if (book().listingForParcel(land.id()) != null) {
            return fail("listed");
        }
        if (book().countOf(player.getUuid()) >= MarketConfig.get().maxListingsPerPlayer()) {
            return fail("limit");
        }
        ListingKind kind = whole ? ListingKind.PARCEL : ListingKind.CHUNKS;
        Listing listing = Listing.land(UUID.randomUUID(), kind, player.getUuid(), player.getGameProfile().getName(),
                serverSale ? Proceeds.LAND_GRANT : Proceeds.SELLER, BigInteger.valueOf(price), currency,
                land.id(), land.name(), land.dimension(), chunks);
        if (!book().add(listing)) {
            return fail("listed");
        }
        LandBridge.bind(land.id(), listing.id());
        data.persistNow();
        MarketSync.broadcast(server);
        String shape = listing.kind() == ListingKind.PARCEL
                ? "整块宗地「" + MarketMessages.parcelName(land.name()) + "」"
                : "从宗地拆出的 " + chunks.size() + " 个区块";
        String pay = serverSale
                ? "货款进入土地出让账户，不进入你的储蓄账户。"
                : "货款交给卖方，不进入中央银行。";
        return changed("listed", List.of(MarketMessages.line("已挂牌 " + listing.shortId() + "。" + shape + "，共 "
                + chunks.size() + " 个区块，价格 " + money(BigInteger.valueOf(price), listing.currency()) + "。" + pay)),
                List.of(view(listing, null)));
    }

    private MarketReply landReady(ServerPlayerEntity player, boolean serverSale) {
        if (!LandBridge.available()) {
            return fail("land_missing");
        }
        if (serverSale && !player.hasPermissionLevel(2)) {
            return fail("permission");
        }
        if (serverSale && !MarketConfig.get().serverLandSales()) {
            return fail("server_disabled");
        }
        return null;
    }

    private void tellSeller(Listing listing, BigInteger amount) {
        ServerPlayerEntity seller = server.getPlayerManager().getPlayer(listing.seller());
        if (seller == null) {
            return;
        }
        if (listing.proceeds() == Proceeds.LAND_GRANT) {
            seller.sendMessage(MarketMessages.line("你持有的服务器出让挂牌 " + listing.shortId() + " 已经售出。货款 "
                    + money(amount, listing.currency()) + " 已进入土地出让账户，没有进入你的储蓄账户。"), false);
            return;
        }
        seller.sendMessage(MarketMessages.line("你的挂牌 " + listing.shortId() + " 已经售出。货款 "
                + money(amount, listing.currency()) + " 已划入你的储蓄账户。"), false);
    }

    private Text boughtLine(Listing listing, BigInteger amount, boolean pending) {
        String pay = listing.proceeds() == Proceeds.LAND_GRANT
                ? "货款已进入土地出让账户。"
                : "货款已划入卖方的储蓄账户。";
        String tail = pending ? "背包空间不足，未能放入的物品会在你下次进入世界时交付。" : "";
        if (listing.kind() == ListingKind.ITEM) {
            return MarketMessages.line("已购买挂牌 " + listing.shortId() + "。", itemName(listing),
                    " × " + listing.count() + "，合计 " + money(amount, listing.currency()) + "。" + pay + tail);
        }
        String shape = listing.kind() == ListingKind.PARCEL ? "整块宗地「" + MarketMessages.parcelName(listing.parcelName()) + "」" : listing.count() + " 个区块";
        return MarketMessages.line("已购买挂牌 " + listing.shortId() + "。" + shape + "，合计 "
                + money(amount, listing.currency()) + "。" + pay);
    }

    private Text summary(Listing listing) {
        String who = listing.proceeds() == Proceeds.LAND_GRANT ? "服务器出让，持有人 " + listing.sellerName() : "卖方 " + listing.sellerName();
        if (listing.kind() == ListingKind.ITEM) {
            String price = listing.priceMode() == PriceMode.SHOP
                    ? "商店参考价格，购买时结算"
                    : "每件 " + money(listing.unitPrice(), listing.currency()) + "，合计 " + money(listing.customTotal(), listing.currency());
            return MarketMessages.line("挂牌 " + listing.shortId() + "：", itemName(listing), " × " + listing.count() + "，" + price + "，" + who + "。");
        }
        String shape = listing.kind() == ListingKind.PARCEL ? "整块宗地「" + MarketMessages.parcelName(listing.parcelName()) + "」" : "区块 " + listing.count() + " 个";
        return MarketMessages.line("挂牌 " + listing.shortId() + "：" + shape + "，" + MarketMessages.dimension(listing.dimension())
                + "，价格 " + money(listing.unitPrice(), listing.currency()) + "，" + who + "。");
    }

    public ListingView view(Listing listing, BigInteger shopUnit) {
        String unit = listing.priceMode() == PriceMode.SHOP ? (shopUnit == null ? "" : shopUnit.toString()) : listing.unitPrice().toString();
        String total;
        if (listing.priceMode() == PriceMode.SHOP) {
            total = shopUnit == null ? "" : shopUnit.multiply(BigInteger.valueOf(listing.count())).toString();
        } else {
            total = listing.customTotal().toString();
        }
        List<String> chunks = new ArrayList<>();
        for (ChunkRef chunk : listing.chunks()) {
            chunks.add(chunk.position());
        }
        return new ListingView(listing.id().toString(), listing.shortId(), listing.kind().token(), listing.sellerName(),
                listing.seller().toString(), listing.proceeds() == Proceeds.LAND_GRANT, listing.priceMode() == PriceMode.SHOP,
                unit, total, listing.currency(), listing.count(), listing.itemId(), listing.dimension(),
                listing.parcelId() == null ? "" : listing.parcelId().toString(), listing.parcelName(), chunks);
    }

    private static BigInteger shopUnit(Listing listing) {
        if (listing.priceMode() != PriceMode.SHOP) {
            return null;
        }
        return ShopPrices.quote(listing);
    }

    private static Text itemName(Listing listing) {
        ItemStack stack = ItemDelivery.read(listing.item());
        if (!stack.isEmpty()) {
            return stack.getName().copy();
        }
        return Text.literal(listing.itemId().isBlank() ? "物品" : listing.itemId());
    }

    private static String money(BigInteger amount, String currency) {
        return amount.toString() + MarketMessages.currency(currency);
    }

    private static String dimension(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().getValue().toString();
    }

    private MarketReply changed(String code, List<Text> lines, List<ListingView> views) {
        return MarketReply.of(true, code, lines, views);
    }

    private MarketReply fail(String code) {
        return MarketReply.message(false, code, MarketMessages.line(sentence(code)));
    }

    public static String sentence(String code) {
        return switch (code) {
            case "missing" -> "没有这块挂牌。请核对编号。";
            case "ambiguous" -> "这个编号对应多块挂牌。请把编号写得更完整。";
            case "self" -> "不能购买自己的挂牌。";
            case "price_moved" -> "当前总价超过确认时的最高总价，未扣款。请刷新报价后重新确认。";
            case "price" -> "价格必须是正整数。";
            case "inventory_pending" -> "物品存档结果尚未确认，已锁定挂牌等待管理员核对。请勿重复挂牌或退款。";
            case "reserved" -> "这块挂牌正在结算，请稍后再试。";
            case "bank_missing" -> "银行模组没有加载，货款无法结算。";
            case "bank_hook_missing" -> "银行尚未提供玩家之间的储蓄划转，货款无法交给卖方。";
            case "land_grant_missing" -> "银行尚未提供土地出让账户，服务器出让土地的货款无法入账。";
            case "insufficient_funds" -> "储蓄余额不足，没有扣款。";
            case "unknown_currency" -> "银行不认识这个币种，没有扣款。";
            case "bank_failed", "bank_pending" -> "银行结算结果尚未确认，挂牌已锁定，请勿重复付款并请管理员核对。";
            case "delivery_pending" -> "货款已处理，但交付结果尚未确认，挂牌已锁定等待核对。";
            case "land_missing" -> "领地模组没有加载，无法转让宗地或区块。";
            case "not_owner" -> "你不是这块宗地的所有者。";
            case "listed" -> "这块宗地已经在市场挂牌。请先撤销原来的挂牌。";
            case "limit" -> "你的挂牌数量已经达到上限。";
            case "empty_hand" -> "请把要出售的物品拿在手上。";
            case "bad_count" -> "数量必须是正整数，并且不能超过手上的这一组物品。";
            case "not_seller" -> "只有卖方可以撤销这块挂牌。";
            case "at_limit" -> "买方持有的区块数量已经达到上限。交易已取消，货款已退回。";
            case "gate" -> "领地转让被其他规则拦住。交易已取消，货款已退回。";
            case "refund_failed" -> "交付没有成功，退回货款也没有成功。请管理员核对这笔交易，挂牌暂时锁定。";
            case "stale" -> "这块宗地的所有者或范围已经变化。挂牌已撤销，货款已退回。";
            case "not_claimed" -> "你站立的位置没有领地。";
            case "mixed" -> "这些区块不属于同一宗地。";
            case "too_many_chunks" -> "一次挂牌的区块数量超过上限。";
            case "no_chunks" -> "这个范围内有尚未占领的区块。";
            case "bad_item" -> "物品数据无法读取。挂牌仍然保留，没有删除物品。若已经扣款，货款已退回。";
            case "delivery" -> "物品或领地没有交付成功，货款已退回。";
            case "permission" -> "只有管理员可以出让服务器土地。";
            case "server_disabled" -> "服务器出让土地已经在配置里关闭。";
            case "shop_missing" -> "商店目前没有这件物品的参考价格。挂牌仍然保留。自定义价格可以照常使用。";
            default -> "这笔交易没有完成。";
        };
    }
}
