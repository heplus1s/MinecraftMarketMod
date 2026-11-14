package dev.jdguo.market;

import net.minecraft.server.MinecraftServer;

/**
 * The dedicated server or the singleplayer integrated server. Remote vanilla
 * clients never set this.
 */
public final class MarketRuntime {
    private static MinecraftServer server;

    private MarketRuntime() {
    }

    public static void attach(MinecraftServer next) {
        server = next;
    }

    public static void detach(MinecraftServer stopping) {
        if (server == stopping) {
            server = null;
        }
    }

    public static MinecraftServer server() {
        return server;
    }
}
