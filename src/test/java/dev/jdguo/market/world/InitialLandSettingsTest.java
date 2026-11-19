package dev.jdguo.market.world;
import java.math.BigInteger;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InitialLandSettingsTest {
    @Test void newWorldUsesRecommendedPriceButDoesNotEnableSales() {
        var s = new InitialLandSettings(); assertEquals(BigInteger.valueOf(32), s.price);
        assertEquals("note", s.currency); assertFalse(s.enabled);
        assertFalse(s.dynamicEnabled); assertEquals(500, s.densityBps);
    }
    @Test void legacyOneAndCustomCurrencyAreNeverOverwritten() {
        var tag = new NbtCompound(); tag.putString("Price", "1"); tag.putString("Currency", "personal:test");
        tag.putBoolean("Enabled", true); tag.putLong("Revision", 12);
        var s = InitialLandSettings.read(tag); assertEquals(BigInteger.ONE, s.price);
        assertEquals("personal:test", s.currency); assertTrue(s.enabled); assertEquals(12, s.revision);
        var reread = InitialLandSettings.read(s.writeNbt(new NbtCompound()));
        assertEquals(s.price, reread.price); assertEquals(s.currency, reread.currency); assertEquals(s.revision, reread.revision);
    }
    @Test void oldDynamicSettingsPreservedAndNewDensityCanBeDisabled() {
        var s = new InitialLandSettings();
        s.dynamicEnabled = true; s.radius = 4; s.ownerBps = 321; s.listingBps = 200;
        var legacy = s.writeNbt(new NbtCompound()); legacy.remove("DensityBps");
        var loaded = InitialLandSettings.read(legacy);
        assertTrue(loaded.dynamicEnabled); assertEquals(4, loaded.radius);
        assertEquals(321, loaded.ownerBps); assertEquals(200, loaded.listingBps);
        assertEquals(500, loaded.densityBps);
        loaded.densityBps = 0;
        assertEquals(0, InitialLandSettings.read(loaded.writeNbt(new NbtCompound())).densityBps);
        loaded.densityBps = 10001;
        assertThrows(IllegalArgumentException.class, loaded::validateDynamic);
    }
    @Test void invalidPriceDisablesSalesRatherThanSellingAtOne() {
        var tag = new NbtCompound(); tag.putString("Price", "-1"); tag.putBoolean("Enabled", true);
        var s = InitialLandSettings.read(tag); assertFalse(s.enabled); assertEquals(BigInteger.valueOf(32), s.price);
    }
}
