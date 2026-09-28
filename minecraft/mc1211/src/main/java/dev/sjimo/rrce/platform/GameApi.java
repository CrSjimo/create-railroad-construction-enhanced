package dev.sjimo.rrce.platform;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class GameApi {
    public static ResourceLocation id(String path) { return ResourceLocation.parse(path); }
    public static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    public static String toJson(Component component) { return Component.Serializer.toJson(component, RegistryAccess.EMPTY); }
    public static Component fromJson(String json) { return Component.Serializer.fromJson(json, RegistryAccess.EMPTY); }
    public static CompoundTag itemTag(ItemStack stack, String key) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.contains(key, 10) ? root.getCompound(key) : null;
    }
    public static CompoundTag editableItemTag(ItemStack stack, String key) {
        CompoundTag existing = itemTag(stack, key);
        return existing == null ? new CompoundTag() : existing;
    }
    public static void storeItemTag(ItemStack stack, String key, CompoundTag tag) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(key, tag));
    }
    public static CompoundTag saveEntity(BlockEntity entity, ServerLevel level) { return entity.saveWithFullMetadata(level.registryAccess()); }
    public static BlockEntity createEntity(BlockPos pos, BlockState state, CompoundTag tag, ServerLevel level) { return BlockEntity.loadStatic(pos, state, tag, level.registryAccess()); }
    public static void loadEntity(BlockEntity entity, CompoundTag tag, ServerLevel level) { entity.loadWithComponents(tag, level.registryAccess()); }
    public static <T extends SavedData> T savedData(MinecraftServer server, Function<CompoundTag, T> load, Supplier<T> create, String id) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(create, (tag, registries) -> load.apply(tag), null), id);
    }
}
