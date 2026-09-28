package dev.sjimo.rrce.testing;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

public final class NbtTestApi {
    public static CompoundTag readCompressed(InputStream stream) throws IOException {
        return NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    }
}
