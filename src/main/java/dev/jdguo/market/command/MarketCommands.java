package dev.jdguo.market.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jdguo.market.message.MarketMessages;
import dev.jdguo.market.listing.DimensionFirstSalePolicy;
import dev.jdguo.market.world.InitialLandSettings;
import dev.jdguo.market.api.SettlementResult;
import dev.jdguo.market.integrate.BankBridge;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.service.MarketDesk;
import dev.jdguo.market.ui.MarketReply;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class MarketCommands {
    private MarketCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(MarketCommands::register);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(MarketCommandNames.ROOT)
                .executes(MarketCommands::usage)
                .then(personalNoteCommands())
                .then(dimensionPriceCommands())
                .then(CommandManager.literal("audit").requires(source -> source.hasPermissionLevel(2)).executes(context -> {
                    var book = MarketDesk.get(context.getSource().getServer()).book();
                    int count = 0;
                    for (var listing : book.all()) {
                        if (listing.status() != dev.jdguo.market.listing.ListingStatus.RESERVED) continue;
                        var deal = book.deals().stream().filter(d -> d.listingId().equals(listing.id())).findFirst();
                        String message = "[市场核对] 挂牌=" + listing.id() + " 类型=" + listing.kind()
                                + " 卖方=" + listing.seller() + " 数量=" + listing.count()
                                + (deal.isPresent() ? " 交易=" + deal.get().id() + " 阶段=" + deal.get().stage()
                                + " 买方=" + deal.get().buyer() + " 银行引用=" + deal.get().reference()
                                : " 库存托管/撤销意图，尚无交易，必须核对背包后处理");
                        context.getSource().sendFeedback(() -> Text.literal(message), false); count++;
                    }
                    for (String owner : book.pendingDeliveryLockOwners()) {
                        context.getSource().sendFeedback(() -> Text.literal("[市场核对] 待领物品交付锁 玩家=" + owner + "；不要直接清锁或重复补发"), false); count++;
                    }
                    if (count == 0) context.getSource().sendFeedback(() -> Text.literal("[市场核对] 没有锁定交易或物品交付"), false);
                    return 1;
                }))
                .then(CommandManager.literal("ui").requires(MarketCommands::player).executes(context -> {
                    dev.jdguo.market.ui.MarketChestScreen.open(player(context)); return 1;
                }))
                .then(currencyListing())
                .then(CommandManager.literal(MarketCommandNames.LIST).requires(MarketCommands::player)
                        .then(CommandManager.literal(MarketCommandNames.SHOP)
                                .executes(context -> listItem(context, true, 0, 0))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 9999))
                                        .executes(context -> listItem(context, true, 0, IntegerArgumentType.getInteger(context, "count")))))
                        .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                                .executes(context -> listItem(context, false, LongArgumentType.getLong(context, "price"), 0))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 9999))
                                        .executes(context -> listItem(context, false, LongArgumentType.getLong(context, "price"), IntegerArgumentType.getInteger(context, "count"))))))
                .then(CommandManager.literal(MarketCommandNames.BUY).requires(MarketCommands::player)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests(MarketCommands::suggestOpen)
                                .executes(context -> reply(context, desk(context).buy(player(context), StringArgumentType.getString(context, "id"))))))
                .then(CommandManager.literal(MarketCommandNames.CANCEL).requires(MarketCommands::player)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests(MarketCommands::suggestOwn)
                                .executes(context -> reply(context, desk(context).cancel(player(context), StringArgumentType.getString(context, "id"), context.getSource().hasPermissionLevel(2))))))
                .then(CommandManager.literal(MarketCommandNames.SEARCH).requires(MarketCommands::player)
                        .executes(context -> reply(context, desk(context).search(player(context), "all", "", 1)))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> reply(context, desk(context).search(player(context), "all", "", IntegerArgumentType.getInteger(context, "page")))))
                        .then(CommandManager.literal(MarketCommandNames.ITEM)
                                .then(CommandManager.argument("item", StringArgumentType.string())
                                        .suggests(MarketCommands::suggestItems)
                                        .executes(context -> reply(context, desk(context).search(player(context), "item", StringArgumentType.getString(context, "item"), 1)))
                                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> reply(context, desk(context).search(player(context), "item", StringArgumentType.getString(context, "item"), IntegerArgumentType.getInteger(context, "page")))))))
                        .then(CommandManager.literal(MarketCommandNames.PLAYER)
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> reply(context, desk(context).search(player(context), "player", StringArgumentType.getString(context, "name"), 1)))
                                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> reply(context, desk(context).search(player(context), "player", StringArgumentType.getString(context, "name"), IntegerArgumentType.getInteger(context, "page")))))))
                        .then(CommandManager.literal(MarketCommandNames.LAND)
                                .executes(context -> reply(context, desk(context).search(player(context), "land", "", 1)))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> reply(context, desk(context).search(player(context), "land", "", IntegerArgumentType.getInteger(context, "page"))))))
                        .then(CommandManager.literal(MarketCommandNames.MINE)
                                .executes(context -> reply(context, desk(context).search(player(context), "mine", "", 1)))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> reply(context, desk(context).search(player(context), "mine", "", IntegerArgumentType.getInteger(context, "page")))))))
                .then(CommandManager.literal(MarketCommandNames.INFO).requires(MarketCommands::player)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests(MarketCommands::suggestOpen)
                                .executes(context -> reply(context, desk(context).info(player(context), StringArgumentType.getString(context, "id"))))))
                .then(CommandManager.literal(MarketCommandNames.PRICE).requires(MarketCommands::player)
                        .executes(context -> reply(context, desk(context).price(player(context)))))
                .then(CommandManager.literal(MarketCommandNames.RELOAD).requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> reply(context, desk(context).reload())))
                .then(CommandManager.literal(MarketCommandNames.LAND).requires(MarketCommands::player)
                        .executes(MarketCommands::usage)
                        .then(CommandManager.literal("repurchase")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests(MarketCommands::suggestOpen)
                                        .executes(context -> {
                                            dev.jdguo.market.ui.GovernmentRepurchaseScreen.open(player(context),
                                                    StringArgumentType.getString(context, "id"));
                                            return 1;
                                        })))
                        .then(landForms(CommandManager.literal(MarketCommandNames.LIST), false))
                        .then(landForms(CommandManager.literal(MarketCommandNames.SERVER).requires(source -> source.hasPermissionLevel(2)), true))
                        .then(CommandManager.literal(MarketCommandNames.SEARCH)
                                .executes(context -> reply(context, desk(context).search(player(context), "land", "", 1)))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> reply(context, desk(context).search(player(context), "land", "", IntegerArgumentType.getInteger(context, "page"))))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> dimensionPriceCommands() {
        return CommandManager.literal("landbase").requires(source -> source.hasPermissionLevel(2))
                .executes(context -> dimensionPrice(context, false))
                .then(CommandManager.argument("netherPercent", IntegerArgumentType.integer(DimensionFirstSalePolicy.NORMAL, DimensionFirstSalePolicy.MAX_PERCENT))
                        .then(CommandManager.argument("endCenterPercent", IntegerArgumentType.integer(DimensionFirstSalePolicy.NORMAL, DimensionFirstSalePolicy.MAX_PERCENT))
                                .then(CommandManager.argument("endCenterRadius", IntegerArgumentType.integer(0,
                                        DimensionFirstSalePolicy.MAX_RADIUS))
                                        .executes(context -> dimensionPrice(context, true)))));
    }

    private static int dimensionPrice(CommandContext<ServerCommandSource> context, boolean update) {
        var source = context.getSource();
        if (!source.hasPermissionLevel(2)) return 0;
        var server = source.getServer();
        var settings = InitialLandSettings.get(server);
        if (update) {
            try {
                settings.setDimensionPricing(server, new DimensionFirstSalePolicy(
                        IntegerArgumentType.getInteger(context, "netherPercent"),
                        IntegerArgumentType.getInteger(context, "endCenterPercent"),
                        IntegerArgumentType.getInteger(context, "endCenterRadius")));
            } catch (RuntimeException failure) {
                source.sendError(Text.literal("维度初售基价设置未保存，请检查服务器日志。"));
                return 0;
            }
        }
        var policy = settings.dimensionPricing;
        source.sendFeedback(() -> Text.literal("系统初售基价：下界 " + policy.netherPercent()
                + "%；末地中心 " + policy.endCenterPercent() + "%，半径 " + policy.endCenterRadius()
                + " 格；其他地块 100%。不影响二手挂牌。"), false);
        return 1;
    }

    /** Explicit currency path, preserving every old /market list command. */
    private static LiteralArgumentBuilder<ServerCommandSource> currencyListing() {
        return CommandManager.literal("offer").requires(MarketCommands::player)
                .then(CommandManager.argument("currency", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            dev.jdguo.market.integrate.MarketCurrencies.options().forEach(o -> builder.suggest(o.id()));
                            return builder.buildFuture();
                        })
                        .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                                .executes(context -> reply(context, desk(context).listItem(player(context), false,
                                        LongArgumentType.getLong(context, "price"), 0, StringArgumentType.getString(context, "currency"))))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 9999))
                                        .executes(context -> reply(context, desk(context).listItem(player(context), false,
                                                LongArgumentType.getLong(context, "price"), IntegerArgumentType.getInteger(context, "count"),
                                                StringArgumentType.getString(context, "currency"))))))
                        .then(CommandManager.literal("shop")
                                .executes(context -> reply(context, desk(context).listItem(player(context), true, 0, 0,
                                        StringArgumentType.getString(context, "currency"))))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 9999))
                                        .executes(context -> reply(context, desk(context).listItem(player(context), true, 0,
                                                IntegerArgumentType.getInteger(context, "count"), StringArgumentType.getString(context, "currency")))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> personalNoteCommands() {
        return CommandManager.literal("note").requires(MarketCommands::player)
                .executes(context -> personalHelp(context))
                .then(CommandManager.literal("search").executes(context -> personalReply(context,
                        BankBridge.personalOffers(player(context).getUuid(), null))))
                .then(CommandManager.literal("view")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(context -> personalReply(context, BankBridge.personalOffers(
                                        player(context).getUuid(), StringArgumentType.getString(context, "name"))))))
                .then(personalQuantity("list", "listPersonal"))
                .then(personalQuantity("quote", "quotePersonal"))
                .then(CommandManager.literal("cancel")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(context -> personalReply(context, BankBridge.personalAmount(
                                        "listPersonal", player(context).getUuid(),
                                        StringArgumentType.getString(context, "name"), BigInteger.ZERO)))))
                .then(CommandManager.literal("buy")
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .then(CommandManager.argument("count", LongArgumentType.longArg(1))
                                        .then(CommandManager.argument("max-total", StringArgumentType.word())
                                                .executes(MarketCommands::buyPersonal)))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> personalQuantity(String command, String hook) {
        return CommandManager.literal(command)
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .then(CommandManager.argument("count", LongArgumentType.longArg(1))
                                .executes(context -> personalReply(context, BankBridge.personalAmount(
                                        hook, player(context).getUuid(), StringArgumentType.getString(context, "name"),
                                        BigInteger.valueOf(LongArgumentType.getLong(context, "count")))))));
    }

    private static int buyPersonal(CommandContext<ServerCommandSource> context) {
        String raw = StringArgumentType.getString(context, "max-total");
        if (!raw.matches("[0-9]{1,32}(\\.[0-9]{1,4})?")) {
            return personalReply(context, SettlementResult.fail("par", ""));
        }
        return personalReply(context, BankBridge.buyPersonal(player(context).getUuid(),
                StringArgumentType.getString(context, "name"),
                BigInteger.valueOf(LongArgumentType.getLong(context, "count")), new BigDecimal(raw)));
    }

    private static int personalHelp(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal(
                "个人币市场：search 浏览，view 查看持有和挂单，list 挂单，cancel 撤单，quote 询价，buy 按最高总价确认买入。"), false);
        return 1;
    }

    private static int personalReply(CommandContext<ServerCommandSource> context, SettlementResult result) {
        String message = result.success() ? result.detail() : switch (result.code()) {
            case "offer_empty" -> "没有足够数量的单笔挂单。";
            case "offer_minimum" -> "订单低于 0.0001 " + MarketMessages.currency("note") + "，请提高买入数量。";
            case "offer_price_moved" -> "应付金额超过你的最高总价，请重新询价。";
            case "funds" -> "持有数量或" + MarketMessages.currency("note") + "余额不足。";
            case "par" -> "数量或" + MarketMessages.currency("note") + "金额无效，最高总价最多四位小数。";
            case "market" -> MarketMessages.currency("note") + "当前无法报价。";
            case "none", "bankrupt" -> "这个个人币不存在或已停止交易。";
            case "bank_missing", "bank_not_ready", "personal_market_missing" -> "个人币市场需要已加载的新版银行模组。";
            default -> "银行未能完成这笔操作。";
        };
        context.getSource().sendFeedback(() -> Text.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> landForms(LiteralArgumentBuilder<ServerCommandSource> node, boolean serverSale) {
        return node
                .then(CommandManager.literal(MarketCommandNames.PARCEL)
                        .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                                .executes(context -> reply(context, desk(context).listParcel(player(context), LongArgumentType.getLong(context, "price"), serverSale)))))
                .then(CommandManager.literal(MarketCommandNames.CHUNK)
                        .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                                .executes(context -> reply(context, desk(context).listChunk(player(context), LongArgumentType.getLong(context, "price"), serverSale)))))
                .then(CommandManager.literal(MarketCommandNames.CHUNKS)
                        .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                                .then(CommandManager.argument("x1", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("z1", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("x2", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("z2", IntegerArgumentType.integer())
                                                                .executes(context -> reply(context, desk(context).listRectangle(
                                                                        player(context),
                                                                        LongArgumentType.getLong(context, "price"),
                                                                        serverSale,
                                                                        IntegerArgumentType.getInteger(context, "x1"),
                                                                        IntegerArgumentType.getInteger(context, "z1"),
                                                                        IntegerArgumentType.getInteger(context, "x2"),
                                                                        IntegerArgumentType.getInteger(context, "z2"))))))))));
    }

    private static int listItem(CommandContext<ServerCommandSource> context, boolean shop, long price, int count) {
        return reply(context, desk(context).listItem(player(context), shop, price, count));
    }

    private static int usage(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> MarketMessages.line(MarketCommandNames.USAGE), false);
        return 0;
    }

    private static int reply(CommandContext<ServerCommandSource> context, MarketReply reply) {
        for (Text line : reply.lines()) {
            context.getSource().sendFeedback(() -> line, false);
        }
        if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
            MarketSync.result(player, reply);
        }
        return reply.ok() ? 1 : 0;
    }

    private static MarketDesk desk(CommandContext<ServerCommandSource> context) {
        return MarketDesk.get(context.getSource().getServer());
    }

    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> context) {
        return (ServerPlayerEntity) context.getSource().getEntity();
    }

    private static boolean player(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity;
    }

    private static CompletableFuture<Suggestions> suggestOpen(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return builder.buildFuture();
        }
        MarketDesk.get(context.getSource().getServer()).book().all().forEach(listing -> builder.suggest(listing.shortId()));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOwn(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            return builder.buildFuture();
        }
        boolean admin = context.getSource().hasPermissionLevel(2);
        MarketDesk.get(context.getSource().getServer()).book().all().forEach(listing -> {
            if (admin || listing.seller().equals(player.getUuid())) {
                builder.suggest(listing.shortId());
            }
        });
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestItems(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT).replace("\"", "");
        int added = 0;
        for (Identifier id : Registries.ITEM.getIds()) {
            String full = id.toString();
            if (remaining.isEmpty() || full.startsWith(remaining) || id.getPath().startsWith(remaining)) {
                builder.suggest(id.getPath());
                if (++added >= 30) {
                    break;
                }
            }
        }
        return builder.buildFuture();
    }
}
