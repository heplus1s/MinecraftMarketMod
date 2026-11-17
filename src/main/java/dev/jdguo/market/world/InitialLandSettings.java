package dev.jdguo.market.world;

import java.math.BigInteger;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Separate world setting: enabling first sales never reprices existing player listings. */
public final class InitialLandSettings extends PersistentState {
    public boolean enabled;
    public static final BigInteger DEFAULT_PRICE = BigInteger.valueOf(32);
    public BigInteger price = DEFAULT_PRICE;
    public String currency = "note";
    public long revision;
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
        } catch (RuntimeException invalid) { s.enabled = false; s.price = DEFAULT_PRICE; }
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        tag.putBoolean("Enabled", enabled); tag.putString("Price", price.toString());
        tag.putString("Currency", currency); tag.putLong("Revision", revision); return tag;
    }
    public void save(MinecraftServer server) { markDirty(); DurableMarketState.save(server, "marketmod_initial_land", this); }
}
