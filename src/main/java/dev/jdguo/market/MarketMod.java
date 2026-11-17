package dev.jdguo.market;

import dev.jdguo.market.command.MarketCommands;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.world.MarketSavedData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class MarketMod implements ModInitializer {
    public static final String MOD_ID = "marketmod";
    public static final Logger LOGGER = LogManager.getLogger("marketmod");

    private static Path configPath = Path.of("config", "marketmod.json");

    public static Path configPath() {
        return configPath;
    }

    private MinecraftServer awaitingRecovery;
    private int recoveryTicks;

    @Override
    public void onInitialize() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("marketmod.json");
        MarketConfig.load(configPath);
        MarketCommands.register();
        dev.jdguo.market.outlet.MarketOutlets.register();
        MarketSync.register();
        dev.jdguo.market.api.LandUiChannel.register();
        ServerLifecycleEvents.SERVER_STARTING.register(MarketRuntime::attach);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MarketRuntime.attach(server);
            MarketSavedData data = MarketSavedData.get(server);
            awaitingRecovery = server;
            recoveryTicks = 0;
            LandBridge.install();
            LOGGER.info("Market loaded {} listings", data.book().size());
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            boolean startup = server == awaitingRecovery;
            if (!startup && ++recoveryTicks < 100) return;
            recoveryTicks = 0;
            if (startup) awaitingRecovery = null;
            try {
                MarketSavedData data = MarketSavedData.get(server);
                boolean pendingLand = data.book().deals().stream().anyMatch(deal ->
                        data.book().get(deal.listingId()).map(listing -> listing.kind() != dev.jdguo.market.listing.ListingKind.ITEM).orElse(false));
                if (startup || pendingLand) data.recover(!startup);
            } catch (RuntimeException failure) {
                // A checkpoint failure must never escape into the server tick or erase the paid journal.
                LOGGER.error("Market recovery deferred; unresolved land deals retry in 100 ticks", failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(MarketRuntime::detach);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MarketSavedData.get(server).deliverPending(handler.player));
    }
}
