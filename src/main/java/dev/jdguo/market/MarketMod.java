package dev.jdguo.market;

import dev.jdguo.market.command.MarketCommands;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.net.MarketSync;
import dev.jdguo.market.world.MarketSavedData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
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

    @Override
    public void onInitialize() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("marketmod.json");
        MarketConfig.load(configPath);
        MarketCommands.register();
        MarketSync.register();
        ServerLifecycleEvents.SERVER_STARTING.register(MarketRuntime::attach);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MarketRuntime.attach(server);
            MarketSavedData data = MarketSavedData.get(server);
            data.recover();
            LandBridge.install();
            LOGGER.info("Market loaded {} listings", data.book().size());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(MarketRuntime::detach);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MarketSavedData.get(server).deliverPending(handler.player));
    }
}
