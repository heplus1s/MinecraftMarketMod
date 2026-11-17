package dev.jdguo.market.listing;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemVariantsTest {
    private NbtCompound stack(int count, int damage) {
        NbtCompound stack = new NbtCompound();
        stack.putString("id", "minecraft:diamond_sword"); stack.putInt("MarketCount", count);
        NbtCompound tag = new NbtCompound(); tag.putInt("Damage", damage); stack.put("tag", tag);
        return stack;
    }
    @Test void quantityDoesNotChangeVariantButDurabilityDoes() {
        assertEquals(ItemVariants.key(stack(1, 7)), ItemVariants.key(stack(100, 7)));
        assertNotEquals(ItemVariants.key(stack(1, 7)), ItemVariants.key(stack(1, 8)));
        assertEquals("default", ItemVariants.key(new NbtCompound()));
        assertEquals("default", ItemVariants.key(stack(1,0)));
    }
    @Test void customNamesEnchantmentsAndTypedTagsStaySeparate() {
        NbtCompound original = stack(1, 0), named = original.copy(), enchanted = original.copy();
        named.getCompound("tag").putString("CustomName", "Legendary");
        enchanted.getCompound("tag").putString("Enchantments", "test");
        assertNotEquals(ItemVariants.key(original), ItemVariants.key(named));
        assertNotEquals(ItemVariants.key(original), ItemVariants.key(enchanted));
        assertNotEquals(ItemVariants.key(named), ItemVariants.key(enchanted));
        NbtCompound otherType = original.copy(); otherType.getCompound("tag").putString("Damage", "0");
        assertNotEquals(ItemVariants.key(original), ItemVariants.key(otherType));
    }
    @Test void compoundInsertionOrderDoesNotChangeIdentity() {
        NbtCompound first = new NbtCompound(), second = new NbtCompound();
        NbtCompound a = new NbtCompound(), b = new NbtCompound();
        a.putInt("x", 1); a.putInt("y", 2); b.putInt("y", 2); b.putInt("x", 1);
        first.put("tag", a); second.put("tag", b);
        assertEquals(ItemVariants.key(first), ItemVariants.key(second));
    }
}
