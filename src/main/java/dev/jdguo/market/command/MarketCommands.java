package dev.jdguo.market.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jdguo.market.message.MarketMessages;
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
                        .then(landForms(CommandManager.literal(MarketCommandNames.LIST), false))
                        .then(landForms(CommandManager.literal(MarketCommandNames.SERVER).requires(source -> source.hasPermissionLevel(2)), true))
                        .then(CommandManager.literal(MarketCommandNames.SEARCH)
                                .executes(context -> reply(context, desk(context).search(player(context), "land", "", 1)))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> reply(context, desk(context).search(player(context), "land", "", IntegerArgumentType.getInteger(context, "page"))))))));
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
