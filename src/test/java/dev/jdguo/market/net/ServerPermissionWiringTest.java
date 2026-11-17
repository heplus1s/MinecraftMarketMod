package dev.jdguo.market.net;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Structural regression for Minecraft-dependent wiring, not an in-game test. */
class ServerPermissionWiringTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/jdguo/market").resolve(relative));
    }
    @Test void managementEntrypointsRetainServerSideChecks() throws Exception {
        assertTrue(source("api/LandMarketTerminal.java").contains("player == null || !player.hasPermissionLevel(2)"));
        assertTrue(source("net/MarketSync.java").contains("case \"server\" -> player.hasPermissionLevel(2)"));
        assertTrue(source("service/MarketDesk.java").contains("serverSale && !player.hasPermissionLevel(2)"));
    }
    @Test void bothQueuedHandshakeAndActionsRejectOldConnection() throws Exception {
        String text=source("net/MarketSync.java");
        String guard="server.getPlayerManager().getPlayer(player.getUuid()) != player || player.networkHandler != handler";
        assertEquals(2, text.split(java.util.regex.Pattern.quote(guard), -1).length - 1);
    }
}
