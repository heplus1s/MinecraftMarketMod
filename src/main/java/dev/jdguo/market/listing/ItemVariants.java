package dev.jdguo.market.listing;

import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Read-only identity of escrow metadata. Never replaces ItemStack.canCombine at checkout. */
public final class ItemVariants {
    private ItemVariants() {}
    public static String key(NbtCompound stack) {
        NbtCompound tag = stack.getCompound("tag");
        if (tag.isEmpty()) return "default";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(tag).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String canonical(NbtElement value) {
        if (value instanceof NbtCompound compound) {
            StringBuilder out = new StringBuilder("{");
            compound.getKeys().stream().sorted().forEach(key -> {
                out.append(key.length()).append(':').append(key).append('=');
                out.append(canonical(compound.get(key))).append(';');
            });
            return out.append('}').toString();
        }
        if (value instanceof NbtList list) {
            StringBuilder out = new StringBuilder("[");
            for (NbtElement child : list) out.append(canonical(child)).append(';');
            return out.append(']').toString();
        }
        return value.getType() + ":" + value;
    }
}
