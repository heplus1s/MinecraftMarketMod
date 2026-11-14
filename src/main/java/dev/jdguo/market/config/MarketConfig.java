package dev.jdguo.market.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.market.MarketMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Flat config. Unknown keys are left in the file. A broken file is not rewritten.
 */
public final class MarketConfig {
    private static volatile MarketConfig current = defaults();

    private final String currency;
    private final int maxListingsPerPlayer;
    private final int pageSize;
    private final int maxChunksPerListing;
    private final boolean serverLandSales;

    private MarketConfig(String currency, int maxListingsPerPlayer, int pageSize, int maxChunksPerListing, boolean serverLandSales) {
        this.currency = currency;
        this.maxListingsPerPlayer = maxListingsPerPlayer;
        this.pageSize = pageSize;
        this.maxChunksPerListing = maxChunksPerListing;
        this.serverLandSales = serverLandSales;
    }

    public static MarketConfig defaults() {
        return new MarketConfig("note", 24, 6, 64, true);
    }

    public static MarketConfig get() {
        return current;
    }

    public static MarketConfig load(Path path) {
        MarketConfig read = read(path);
        current = read;
        return read;
    }

    public static MarketConfig read(Path path) {
        if (path == null || !Files.exists(path)) {
            writeDefaults(path);
            return defaults();
        }
        String raw;
        try {
            raw = Files.readString(path);
        } catch (IOException exception) {
            MarketMod.LOGGER.error("Could not read {}", path, exception);
            return defaults();
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.error("Market config is not valid JSON. Using defaults and leaving the file in place.");
            return defaults();
        }
        if (!parsed.isJsonObject()) {
            MarketMod.LOGGER.error("Market config must be a JSON object. Using defaults.");
            return defaults();
        }
        return from(parsed.getAsJsonObject());
    }

    public static void writeDefaults(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    {
                      "currency": "note",
                      "maxListingsPerPlayer": 24,
                      "pageSize": 6,
                      "maxChunksPerListing": 64,
                      "serverLandSales": true
                    }
                    """);
        } catch (IOException exception) {
            MarketMod.LOGGER.error("Could not write {}", path, exception);
        }
    }

    static MarketConfig from(JsonObject object) {
        MarketConfig base = defaults();
        String currency = text(object, "currency", base.currency);
        if (!currency.matches("[a-z0-9_]{1,32}")) {
            MarketMod.LOGGER.error("Market currency id is invalid. Using note.");
            currency = "note";
        }
        return new MarketConfig(
                currency,
                clamp(number(object, "maxListingsPerPlayer", base.maxListingsPerPlayer), 1, 500),
                clamp(number(object, "pageSize", base.pageSize), 1, 50),
                clamp(number(object, "maxChunksPerListing", base.maxChunksPerListing), 1, 4096),
                bool(object, "serverLandSales", base.serverLandSales));
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static int number(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public String currency() {
        return currency;
    }

    public int maxListingsPerPlayer() {
        return maxListingsPerPlayer;
    }

    public int pageSize() {
        return pageSize;
    }

    public int maxChunksPerListing() {
        return maxChunksPerListing;
    }

    public boolean serverLandSales() {
        return serverLandSales;
    }
}
