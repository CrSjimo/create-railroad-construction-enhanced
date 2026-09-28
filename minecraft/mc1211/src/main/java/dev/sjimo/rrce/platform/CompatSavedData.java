package dev.sjimo.rrce.platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
public abstract class CompatSavedData extends SavedData {
    public abstract CompoundTag save(CompoundTag root);
    @Override public final CompoundTag save(CompoundTag root, HolderLookup.Provider registries) { return save(root); }
}
