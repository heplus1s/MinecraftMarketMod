package dev.jdguo.market.world;

import dev.jdguo.market.listing.DimensionFirstSalePolicy;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InitialLandSettingsDimensionTest {
    @Test void oldWorldUsesDefaultPremiumWithoutChangingOtherSettings() {
        var source = new InitialLandSettings();
        source.enabled = true;
        source.revision = 17;
        var old = source.writeNbt(new NbtCompound());
        old.remove("DimensionFirstSale");

        var loaded = InitialLandSettings.read(old);
        assertTrue(loaded.enabled);
        assertFalse(loaded.dynamicEnabled);
        assertEquals(17, loaded.revision);
        assertEquals(DimensionFirstSalePolicy.DEFAULT, loaded.dimensionPricing);
    }

    @Test void customPolicySurvivesRoundTripAndInvalidPolicyDisablesSale() {
        var source = new InitialLandSettings();
        source.enabled = true;
        source.dimensionPricing = new DimensionFirstSalePolicy(170, 225, 512);
        var nbt = source.writeNbt(new NbtCompound());

        var loaded = InitialLandSettings.read(nbt);
        assertTrue(loaded.enabled);
        assertEquals(source.dimensionPricing, loaded.dimensionPricing);

        nbt.getCompound("DimensionFirstSale").putInt("NetherPercent", 5);
        assertFalse(InitialLandSettings.read(nbt).enabled);
    }
}
