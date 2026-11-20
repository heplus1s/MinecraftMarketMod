package dev.jdguo.market.world;

import java.math.BigInteger;
import dev.jdguo.market.listing.DimensionFirstSalePolicy;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Separate world setting: enabling first sales never reprices existing player listings. */
public final class InitialLandSettings extends PersistentState {
    public boolean enabled;
    public static final BigInteger DEFAULT_PRICE = BigInteger.valueOf(32);
    public BigInteger price = DEFAULT_PRICE;
    public String currency = "note";
    public long revision;
    public DimensionFirstSalePolicy dimensionPricing = DimensionFirstSalePolicy.DEFAULT;
    public boolean dynamicEnabled; // Existing worlds retain static pricing until explicitly enabled.
    public static final int DEFAULT_DENSITY_BPS = 500;
    public int densityBps = DEFAULT_DENSITY_BPS;
    public int radius = 8, ownerBps = 500, listingBps = 100, saleBps = 300, decayHours = 168, listingMaxPercent = 120;

    public static InitialLandSettings get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(InitialLandSettings::read, InitialLandSettings::new, "marketmod_initial_land");
    }
    static InitialLandSettings read(NbtCompound tag) {
        InitialLandSettings s = new InitialLandSettings();
        try {
            s.price = new BigInteger(tag.getString("Price"));
            if (s.price.signum() <= 0) throw new IllegalArgumentException();
            s.currency = tag.getString("Currency");
            s.enabled = tag.getBoolean("Enabled");
            s.revision = tag.getLong("Revision");
            s.dynamicEnabled = tag.getBoolean("DynamicEnabled");
            if (tag.contains("Radius")) {
                s.radius = tag.getInt("Radius"); s.ownerBps = tag.getInt("OwnerBps");
                s.listingBps = tag.getInt("ListingBps"); s.saleBps = tag.getInt("SaleBps"); s.decayHours = tag.getInt("DecayHours");
                if (tag.contains("ListingMaxPercent")) s.listingMaxPercent = tag.getInt("ListingMaxPercent");
                if (tag.contains("DensityBps")) s.densityBps = tag.getInt("DensityBps");
                s.validateDynamic();
            }
        } catch (RuntimeException invalid) { s.enabled = false; s.dynamicEnabled = false; s.price = DEFAULT_PRICE; s.radius = 8; s.ownerBps = 500; s.densityBps = DEFAULT_DENSITY_BPS; s.listingBps = 100; s.saleBps = 300; s.decayHours = 168; s.listingMaxPercent = 120; }
        if (tag.contains("DimensionFirstSale")) {
            // Invalid premium data must not silently enable purchases at the wrong price.
            try {
                var dimensions = tag.getCompound("DimensionFirstSale");
                if (!dimensions.contains("NetherPercent", NbtElement.INT_TYPE)
                        || !dimensions.contains("EndCenterPercent", NbtElement.INT_TYPE)
                        || !dimensions.contains("EndCenterRadius", NbtElement.INT_TYPE)) throw new IllegalArgumentException();
                s.dimensionPricing = new DimensionFirstSalePolicy(dimensions.getInt("NetherPercent"),
                        dimensions.getInt("EndCenterPercent"), dimensions.getInt("EndCenterRadius"));
            } catch (IllegalArgumentException invalid) { s.enabled = false; }
        }
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        tag.putBoolean("Enabled", enabled); tag.putString("Price", price.toString());
        tag.putString("Currency", currency); tag.putLong("Revision", revision);
        tag.putBoolean("DynamicEnabled", dynamicEnabled); tag.putInt("Radius", radius); tag.putInt("OwnerBps", ownerBps);
        var dimensions = new NbtCompound();
        dimensions.putInt("NetherPercent", dimensionPricing.netherPercent());
        dimensions.putInt("EndCenterPercent", dimensionPricing.endCenterPercent());
        dimensions.putInt("EndCenterRadius", dimensionPricing.endCenterRadius());
        tag.put("DimensionFirstSale", dimensions);
        tag.putInt("DensityBps", densityBps); tag.putInt("ListingBps", listingBps); tag.putInt("SaleBps", saleBps); tag.putInt("DecayHours", decayHours); tag.putInt("ListingMaxPercent", listingMaxPercent); return tag;
    }
    public void validateDynamic() {
        if (radius < 0 || radius > 128 || ownerBps < 0 || ownerBps > 10000 || densityBps < 0 || densityBps > 10000 || listingBps < 0 || listingBps > 10000
                || saleBps < 0 || saleBps > 10000 || decayHours < 1 || decayHours > 720 || listingMaxPercent < 1 || listingMaxPercent > 10000) throw new IllegalArgumentException("dynamic settings");
    }
    public void setDimensionPricing(MinecraftServer server, DimensionFirstSalePolicy proposed) {
        var old = dimensionPricing;
        long oldRevision = revision;
        dimensionPricing = java.util.Objects.requireNonNull(proposed);
        try {
            revision = Math.incrementExact(revision);
            save(server);
        } catch (RuntimeException failure) {
            dimensionPricing = old;
            revision = oldRevision;
            markDirty();
            throw failure;
        }
    }

    public void save(MinecraftServer server) { markDirty(); DurableMarketState.save(server, "marketmod_initial_land", this); }
}
