package dev.jdguo.market.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;

/** Transaction checkpoints must report write failures, unlike vanilla's log-only save method. */
final class DurableMarketState {
    private DurableMarketState() {}
    static void save(MinecraftServer server, String id, PersistentState state) {
        if (!server.isOnThread()) throw new IllegalStateException("server thread required");
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(id + ".dat");
        Path temporary = file.resolveSibling(id + ".checkpoint.tmp");
        NbtCompound expected = state.writeNbt(new NbtCompound());
        try {
            Files.createDirectories(file.getParent());
            NbtCompound root = new NbtCompound();
            root.put("data", expected);
            root.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
            NbtIo.writeCompressed(root, temporary.toFile());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            NbtCompound read = NbtIo.readCompressed(file.toFile());
            if (read == null || !expected.equals(read.getCompound("data"))) throw new IOException("market checkpoint read-back mismatch");
            state.setDirty(false);
        } catch (IOException | RuntimeException error) {
            state.markDirty();
            throw new IllegalStateException("Market checkpoint not confirmed", error);
        }
    }
}
