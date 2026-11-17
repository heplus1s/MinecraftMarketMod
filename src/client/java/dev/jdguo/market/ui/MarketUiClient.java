package dev.jdguo.market.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class MarketUiClient implements ClientModInitializer {
    static final Identifier HELLO = new Identifier("marketmod", "hello");
    static final Identifier ACTION = new Identifier("marketmod", "action");
    static final Identifier SYNC = new Identifier("marketmod", "sync");
    static final Identifier RESULT = new Identifier("marketmod", "result");
    private static CatalogPages catalogPages = new CatalogPages();
    private static CatalogPages.Snapshot latestCatalog;
    private static JsonObject latest = new JsonObject();
    private static boolean openRequested;
    private static int awaitingOpen;
    private static boolean personalRequested;
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("marketmod", "open"), (client, handler, buf, response) -> {
            String json = buf.readString(262144);
            client.execute(() -> {
                try { latest = JsonParser.parseString(json).getAsJsonObject(); awaitingOpen = 0; open(client); }
                catch (RuntimeException ignored) { }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("marketmod", "catalog"), (client, handler, buf, response) -> {
            String json = buf.readString(262144);
            client.execute(() -> {
                try {
                    var snapshot = catalogPages.accept(JsonParser.parseString(json).getAsJsonObject());
                    if (snapshot != null) {
                        latestCatalog = snapshot;
                        if (client.currentScreen instanceof MarketScreen screen) screen.catalog(snapshot.currency(), snapshot.items());
                    }
                } catch (RuntimeException ignored) { }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("marketmod", "personal"), (client, handler, buf, response) -> {
            String json=buf.readString(262144);
            client.execute(()->{try{if(client.currentScreen instanceof PersonalMarketScreen screen)screen.accept(JsonParser.parseString(json).getAsJsonObject());}catch(RuntimeException ignored){}});
        });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("marketmod", "system_shop"), (client, handler, buf, response) -> {
            String json=buf.readString(32767);
            client.execute(()->{try{if(client.currentScreen instanceof SystemShopScreen screen)screen.accept(JsonParser.parseString(json).getAsJsonObject());}catch(RuntimeException ignored){}});
        });
        LandConfirmScreen.register();
        ClientPlayNetworking.registerGlobalReceiver(SYNC, (client, handler, buf, response) -> {
            String json = buf.readString(262144);
            client.execute(() -> {
                try {
                    latest = JsonParser.parseString(json).getAsJsonObject();
                    if (client.currentScreen instanceof MarketScreen screen) screen.update(latest);
                    else if (awaitingOpen > 0) open(client);
                    awaitingOpen = 0;
                } catch (RuntimeException ignored) { }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RESULT, (client, handler, buf, response) -> {
            String json = buf.readString(262144);
            client.execute(() -> {
                try {
                    JsonObject result = JsonParser.parseString(json).getAsJsonObject();
                    if (client.currentScreen instanceof MarketScreen screen) screen.result(result);
                } catch (RuntimeException ignored) { }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            latest = new JsonObject();
            latestCatalog = null;
            catalogPages = new CatalogPages();
            openRequested = false;
            personalRequested = false;
            awaitingOpen = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Defer beyond ChatScreen's command submission/close call stack.
            if (openRequested) {
                openRequested = false;
                if (client.player == null) return;
                if (!available()) {
                    inform(client, "服务器未提供市场界面接口，请检查服务端 marketmod；原版菜单使用 /market ui。");
                    return;
                }
                awaitingOpen = 100;
                refresh();
            } else if (awaitingOpen > 0 && --awaitingOpen == 0) {
                inform(client, "市场界面未收到服务器响应，请检查两端模组版本；可尝试 /market ui。");
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("marketnoteui").executes(context -> {personalRequested=true;openRequested=true;return 1;})));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("marketui").executes(context -> {
            openRequested = true;
            return 1;
        })));
    }
    /** Optional bank-client bridge: no server command or chat screen is required. */
    public static boolean requestPersonalMarket() {
        if (!available()) return false;
        personalRequested = true;
        openRequested = true;
        return true;
    }
    private static void open(MinecraftClient client) {
        var screen = new MarketScreen(latest);
        client.setScreen(screen);
        if(personalRequested){personalRequested=false;client.setScreen(new PersonalMarketScreen(screen));action("notes");return;}
        if (latestCatalog != null) screen.catalog(latestCatalog.currency(), latestCatalog.items());
        String currency = MarketScreen.text(latest, "defaultCurrency");
        catalog(currency.isBlank() ? "note" : currency);
    }
    static boolean catalog(String currency) { return action("catalog " + currency); }
    private static void inform(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), false);
    }
    static boolean available() { return ClientPlayNetworking.canSend(HELLO) && ClientPlayNetworking.canSend(ACTION); }
    static boolean refresh() {
        if (!available()) return false;
        var buf = PacketByteBufs.create(); buf.writeVarInt(1); ClientPlayNetworking.send(HELLO, buf); return true;
    }
    static boolean action(String action) {
        if (!available()) return false;
        var buf = PacketByteBufs.create(); buf.writeString(action, 512); ClientPlayNetworking.send(ACTION, buf); return true;
    }
}
