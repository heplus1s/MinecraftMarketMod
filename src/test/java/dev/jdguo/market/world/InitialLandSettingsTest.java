package dev.jdguo.market.world;
import java.math.BigInteger;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InitialLandSettingsTest {
    @Test void newWorldUsesRecommendedPriceButDoesNotEnableSales() {
        var s = new InitialLandSettings(); assertEquals(BigInteger.valueOf(32), s.price);
        assertEquals("note", s.currency); assertFalse(s.enabled);
    }
    @Test void legacyOneAndCustomCurrencyAreNeverOverwritten() {
        var tag = new NbtCompound(); tag.putString("Price", "1"); tag.putString("Currency", "personal:test");
        tag.putBoolean("Enabled", true); tag.putLong("Revision", 12);
        var s = InitialLandSettings.read(tag); assertEquals(BigInteger.ONE, s.price);
        assertEquals("personal:test", s.currency); assertTrue(s.enabled); assertEquals(12, s.revision);
        var reread = InitialLandSettings.read(s.writeNbt(new NbtCompound()));
        assertEquals(s.price, reread.price); assertEquals(s.currency, reread.currency); assertEquals(s.revision, reread.revision);
    }
    @Test void invalidPriceDisablesSalesRatherThanSellingAtOne() {
        var tag = new NbtCompound(); tag.putString("Price", "-1"); tag.putBoolean("Enabled", true);
        var s = InitialLandSettings.read(tag); assertFalse(s.enabled); assertEquals(BigInteger.valueOf(32), s.price);
    }
}
