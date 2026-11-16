package dev.jdguo.market.integrate;

import dev.jdguo.market.MarketMod;
import dev.jdguo.market.MarketRuntime;
import dev.jdguo.market.listing.ChunkRef;
import dev.jdguo.market.listing.Handoff;
import dev.jdguo.market.listing.LandPermit;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingGuard;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.ListingStatus;
import dev.jdguo.market.listing.SaleReference;
import dev.jdguo.market.world.MarketSavedData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Soft link to {@code dev.jdguo.land.api.LandApi}. The market jar does not
 * contain the land mod. Purchases call {@code transferParcel} or
 * {@code transferChunks} with reference {@code sale:} plus the market listing id.
 */
public final class LandBridge {
    public static final String FLAG = "market_listing";

    private static boolean installed;

    private LandBridge() {
    }

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("landmod");
    }

    public static void install() {
        if (installed || !available()) {
            return;
        }
        try {
            Class<?> gateType = Class.forName("dev.jdguo.land.integration.ClaimGate");
            Class<?> listenerType = Class.forName("dev.jdguo.land.integration.LandListener");
            Class<?> api = Class.forName("dev.jdguo.land.api.LandApi");
            Object gate = Proxy.newProxyInstance(gateType.getClassLoader(), new Class<?>[]{gateType}, LandBridge::gate);
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, LandBridge::listen);
            api.getMethod("registerGate", gateType).invoke(null, gate);
            api.getMethod("registerListener", listenerType).invoke(null, listener);
            installed = true;
            MarketMod.LOGGER.info("Listed parcels are locked until the market sale reference is presented");
        } catch (ReflectiveOperationException exception) {
            MarketMod.LOGGER.error("The land mod is loaded, but its transfer API could not be reached", exception);
        }
    }

    public static LandView at(MinecraftServer server, String dimension, int chunkX, int chunkZ) {
        Object info = call("parcelAt", new Class<?>[]{MinecraftServer.class, String.class, int.class, int.class}, server, dimension, chunkX, chunkZ);
        return view(info);
    }

    public static LandView parcel(MinecraftServer server, UUID parcelId) {
        Object info = call("parcel", new Class<?>[]{MinecraftServer.class, UUID.class}, server, parcelId);
        return view(info);
    }

    public static void bind(UUID parcelId, UUID listingId) {
        flag(parcelId, listingId == null ? "" : listingId.toString());
    }

    public static void clear(UUID parcelId) {
        flag(parcelId, "");
    }

    public static Handoff.Outcome transfer(Listing listing, UUID buyer, String buyerName) {
        if (!available()) {
            return Handoff.Outcome.failure("land_missing");
        }
        MinecraftServer server = MarketRuntime.server();
        if (server == null || listing.parcelId() == null) {
            return Handoff.Outcome.failure("land_missing");
        }
        String reference = SaleReference.of(listing.id());
        if (listing.initialGrant()) {
            String result = initialGrant(server, buyer, listing.dimension(), coordinates(listing.chunks()), reference);
            if ("applied".equals(result)) return Handoff.Outcome.success(null);
            if (result.startsWith("rejected:")) return Handoff.Outcome.drop("stale");
            // pending/conflict/missing API are ambiguous: retain the PAID journal, never refund blindly.
            throw new IllegalStateException("Initial land grant outcome: " + result);
        }
        try {
            if (listing.kind() == ListingKind.PARCEL) {
                return transferParcel(server, listing, buyer, buyerName, reference);
            }
            return transferChunks(server, listing, buyer, buyerName, reference);
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.error("Land transfer failed for listing {}", listing.id(), exception);
            // A transfer may already have changed ownership before a sync failure.
            // Let checkout retain its PAID journal; recovery rechecks ownership.
            throw exception;
        }
    }

    private static Handoff.Outcome transferParcel(MinecraftServer server, Listing listing, UUID buyer, String buyerName, String reference) {
        LandView now = parcel(server, listing.parcelId());
        if (now == null) {
            return Handoff.Outcome.drop("stale");
        }
        if (now.owner().equals(buyer) && now.same(listing.chunks())) {
            return Handoff.Outcome.success(null);
        }
        if (!now.owner().equals(listing.seller()) || !now.same(listing.chunks())) {
            return Handoff.Outcome.drop("stale");
        }
        Object result = LandPermit.call(reference, () -> call("transferParcel",
                new Class<?>[]{MinecraftServer.class, UUID.class, UUID.class, UUID.class, String.class, String.class},
                server, listing.seller(), listing.parcelId(), buyer, buyerName, reference));
        return map(result);
    }

    private static Handoff.Outcome transferChunks(MinecraftServer server, Listing listing, UUID buyer, String buyerName, String reference) {
        boolean allBuyer = true;
        boolean allSeller = true;
        for (ChunkRef chunk : listing.chunks()) {
            LandView now = at(server, chunk.dimension(), chunk.x(), chunk.z());
            if (now == null || !now.chunks().contains(chunk)) {
                return Handoff.Outcome.drop("stale");
            }
            if (!now.owner().equals(buyer)) {
                allBuyer = false;
            }
            if (!now.owner().equals(listing.seller()) || !now.id().equals(listing.parcelId())) {
                allSeller = false;
            }
        }
        if (allBuyer) {
            return Handoff.Outcome.success(null);
        }
        if (!allSeller) {
            return Handoff.Outcome.drop("stale");
        }
        List<Object> keys = claimKeys(listing.chunks());
        Object result = LandPermit.call(reference, () -> call("transferChunks",
                new Class<?>[]{MinecraftServer.class, UUID.class, List.class, UUID.class, String.class, String.class},
                server, listing.seller(), keys, buyer, buyerName, reference));
        return map(result);
    }

    public static String initialGrantCheck(MinecraftServer server, UUID buyer, String dimension, int[] coordinates) {
        Object result = call("initialGrantCheck", new Class<?>[]{MinecraftServer.class, UUID.class, String.class, int[].class}, server, buyer, dimension, coordinates);
        return result == null ? "rejected:领地接口不可用" : String.valueOf(result);
    }

    private static String initialGrant(MinecraftServer server, UUID buyer, String dimension, int[] coordinates, String reference) {
        Object result = call("initialGrant", new Class<?>[]{MinecraftServer.class, UUID.class, String.class, int[].class, String.class}, server, buyer, dimension, coordinates, reference);
        return result == null ? "pending" : String.valueOf(result);
    }

    public static int[] coordinates(List<ChunkRef> chunks) {
        int[] result = new int[chunks.size() * 2];
        for (int i = 0; i < chunks.size(); i++) { result[i * 2] = chunks.get(i).x(); result[i * 2 + 1] = chunks.get(i).z(); }
        return result;
    }

    private static Object call(String name, Class<?>[] types, Object... args) {
        if (!available()) {
            return null;
        }
        try {
            Class<?> api = Class.forName("dev.jdguo.land.api.LandApi");
            Method method = api.getMethod(name, types);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Handoff.Outcome map(Object result) {
        if (result == null) {
            return Handoff.Outcome.failure("land_missing");
        }
        try {
            boolean ok = (boolean) result.getClass().getMethod("succeeded").invoke(result);
            if (ok) {
                return Handoff.Outcome.success(null);
            }
            Object denial = result.getClass().getMethod("denial").invoke(result);
            String name = String.valueOf(denial);
            return switch (name) {
                case "AT_LIMIT" -> Handoff.Outcome.failure("at_limit");
                case "GATE" -> Handoff.Outcome.failure("gate");
                case "SELF" -> Handoff.Outcome.failure("self");
                case "NOT_OWNER", "NOT_CLAIMED", "PARCEL_NOT_FOUND", "SALE_STALE" -> Handoff.Outcome.drop("stale");
                default -> Handoff.Outcome.failure("delivery");
            };
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<Object> claimKeys(List<ChunkRef> chunks) {
        try {
            Class<?> keyType = Class.forName("dev.jdguo.land.claim.ClaimKey");
            var constructor = keyType.getConstructor(String.class, int.class, int.class);
            List<Object> keys = new ArrayList<>();
            for (ChunkRef chunk : chunks) {
                keys.add(constructor.newInstance(chunk.dimension(), chunk.x(), chunk.z()));
            }
            return keys;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void flag(UUID parcelId, String value) {
        MinecraftServer server = MarketRuntime.server();
        if (!available() || server == null || parcelId == null) {
            return;
        }
        try {
            Class<?> api = Class.forName("dev.jdguo.land.api.LandApi");
            api.getMethod("putParcelFlag", MinecraftServer.class, UUID.class, String.class, NbtElement.class)
                    .invoke(null, server, parcelId, FLAG, NbtString.of(value));
        } catch (ReflectiveOperationException exception) {
            MarketMod.LOGGER.warn("Could not store the market marker on parcel {}", parcelId, exception);
        }
    }

    private static Object gate(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return objectCall(method);
        }
        MarketSavedData data = data();
        if (data == null || data.book().size() == 0) {
            return true;
        }
        try {
            ListingGuard guard = new ListingGuard(data.book());
            return switch (method.getName()) {
                case "allowParcelTransfer" -> guard.allowsParcelTransfer(parcelId(args[1]), text(args, 3));
                case "allowTransfer" -> guard.allowsChunkTransfer(claimParcel(args[1]), claimChunk(args[1]), args.length >= 4 ? text(args, 3) : "");
                case "allowSplit" -> guard.allowsSplit(parcelId(args[1]), claimList(args[2]));
                case "allowAbandon" -> guard.allowsAbandon(claimParcel(args[1]));
                case "allowMerge" -> guard.allowsMerge(parcelId(args[1]), parcelId(args[2]));
                case "allowJoin" -> guard.allowsJoin(parcelId(args[1]));
                default -> true;
            };
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.error("Could not check a listed parcel", exception);
            return !locks(method.getName());
        }
    }

    private static Object listen(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return objectCall(method);
        }
        try {
            switch (method.getName()) {
                case "afterParcelTransfer", "afterSeizeParcel" -> external(parcelId(args[0]), text(args, 3));
                case "afterChunkTransfer", "afterSeizeChunk" -> {
                    external(uuid(args[1]), text(args, 5));
                    external(parcelId(args[2]), text(args, 5));
                }
                case "afterSplit" -> {
                    external(parcelId(args[0]), null);
                    external(parcelId(args[1]), null);
                }
                case "afterMerge" -> {
                    external(parcelId(args[0]), null);
                    external(uuid(args[1]), null);
                }
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            MarketMod.LOGGER.error("Land listener failed", exception);
        }
        return null;
    }

    private static void external(UUID parcelId, String reference) {
        MarketSavedData data = data();
        if (data == null || parcelId == null) {
            return;
        }
        Listing listing = data.book().listingForParcel(parcelId);
        if (listing == null || listing.status() != ListingStatus.OPEN) {
            return;
        }
        if (SaleReference.matches(listing.id(), reference)) {
            return;
        }
        data.book().remove(listing.id());
        clear(parcelId);
        data.persistNow();
    }

    private static MarketSavedData data() {
        MinecraftServer server = MarketRuntime.server();
        if (server == null) {
            return null;
        }
        return MarketSavedData.get(server);
    }

    private static boolean locks(String name) {
        return switch (name) {
            case "allowParcelTransfer", "allowTransfer", "allowSplit", "allowAbandon", "allowMerge", "allowJoin" -> true;
            default -> false;
        };
    }

    private static Object objectCall(Method method) {
        return switch (method.getName()) {
            case "toString" -> "marketmod-land";
            case "hashCode" -> 0;
            default -> false;
        };
    }

    private static LandView view(Object info) {
        if (info == null) {
            return null;
        }
        UUID id = (UUID) call(info, "id");
        List<?> raw = (List<?>) call(info, "chunks");
        List<ChunkRef> chunks = new ArrayList<>();
        if (raw != null) {
            for (Object key : raw) {
                chunks.add(new ChunkRef((String) call(key, "dimension"), (Integer) call(key, "chunkX"), (Integer) call(key, "chunkZ")));
            }
        }
        return new LandView(id, (String) call(info, "name"), (UUID) call(info, "owner"), (String) call(info, "ownerName"),
                (String) call(info, "dimension"), (Integer) call(info, "chunkCount"), chunks);
    }

    private static UUID parcelId(Object parcel) {
        return (UUID) call(parcel, "id");
    }

    private static UUID claimParcel(Object claim) {
        return (UUID) call(claim, "parcelId");
    }

    private static ChunkRef claimChunk(Object claim) {
        Object key = call(claim, "key");
        return new ChunkRef((String) call(key, "dimension"), (Integer) call(key, "chunkX"), (Integer) call(key, "chunkZ"));
    }

    private static List<ChunkRef> claimList(Object raw) {
        List<ChunkRef> chunks = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object claim : list) {
                chunks.add(claimChunk(claim));
            }
        }
        return chunks;
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : null;
    }

    private static String text(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null) {
            return "";
        }
        return String.valueOf(args[index]);
    }

    private static Object call(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(name, exception);
        }
    }

}
