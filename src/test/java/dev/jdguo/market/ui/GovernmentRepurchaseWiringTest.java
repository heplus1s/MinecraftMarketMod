package dev.jdguo.market.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Structural wiring checks only; handler flow is additionally checked with Minecraft stubs. */
class GovernmentRepurchaseWiringTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/jdguo/market").resolve(relative));
    }

    @Test void commandOpensPreviewRatherThanDebiting() throws Exception {
        String commands = source("command/MarketCommands.java");
        int start = commands.indexOf("CommandManager.literal(\"repurchase\")");
        String entry = commands.substring(start, commands.indexOf(".then(landForms", start));
        assertTrue(entry.contains("source.hasPermissionLevel(2)"));
        assertTrue(entry.contains("GovernmentRepurchaseScreen.open"));
        assertFalse(entry.contains("GovernmentRepurchase.confirm"));
    }

    @Test void dedicatedListHasNoSettlementAndPreservesExactListingId() throws Exception {
        String cmd = source("command/MarketCommands.java");
        assertTrue(cmd.contains("GovernmentRepurchaseListScreen.open(player(context))"));
        String list = source("ui/GovernmentRepurchaseListScreen.java");
        assertTrue(list.contains("viewer.currentScreenHandler != this || !canUse(player)"));
        assertTrue(list.contains("viewer.hasPermissionLevel(2)"));
        assertTrue(list.contains("GovernmentRepurchase::eligible"));
        assertTrue(list.contains("shown.add(listing.id())"));
        assertTrue(list.contains("GovernmentRepurchaseScreen.open(viewer, shown.get(slot).toString(), true)"));
        assertFalse(list.contains("GovernmentRepurchase.confirm"));
        String client = Files.readString(Path.of("src/client/java/dev/jdguo/market/ui/MarketScreen.java"));
        assertTrue(client.contains("sendChatCommand(\"market land repurchase\")"));
        assertTrue(client.contains("systemRepurchaseList"));
    }

    @Test void screenUsesVanillaPacketsAndSingleUsePermissionGuard() throws Exception {
        String screen = source("ui/GovernmentRepurchaseScreen.java");
        assertTrue(screen.contains("ScreenHandlerType.GENERIC_9X6"));
        assertTrue(screen.contains("viewer.currentScreenHandler != this || consumed"));
        assertTrue(screen.contains("!viewer.hasPermissionLevel(2)"));
        int submit = screen.indexOf("GovernmentRepurchase.confirm(viewer, snapshot)");
        int click = screen.indexOf("if (slot == 49)");
        assertTrue(screen.indexOf("consumed = true", click) < submit);
        assertTrue(screen.indexOf("viewer.closeHandledScreen()", click) < submit);
        assertEquals(1, screen.split("GovernmentRepurchase.confirm", -1).length - 1);
    }

    @Test void previewShowsMaterialDetailsAndExactScopeWithoutOldOwnerLabel() throws Exception {
        String screen = source("ui/GovernmentRepurchaseScreen.java");
        for (String field : new String[]{"listing.id()", "listing.seller()", "listing.sellerName()",
                "snapshot.total()", "listing.currency()", "listing.chunks()", "chunk.dimension()",
                "chunk.x()", "chunk.z()", "land_grant", "服务器用地"}) assertTrue(screen.contains(field), field);
        assertFalse(screen.contains("过户政府"));
        String open = screen.substring(screen.indexOf("public static void open"), screen.indexOf("private void icon"));
        assertTrue(open.contains("GovernmentRepurchase.preview"));
        assertFalse(open.contains("GovernmentRepurchase.confirm"));
    }
}
