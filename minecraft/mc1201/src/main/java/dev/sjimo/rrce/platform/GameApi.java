package dev.sjimo.rrce.platform;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class GameApi {
    public static ResourceLocation id(String path) { return new ResourceLocation(path); }
    public static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    public static String toJson(Component component) { return Component.Serializer.toJson(component); }
    public static Component fromJson(String json) { return Component.Serializer.fromJson(json); }
    public static CompoundTag itemTag(ItemStack stack, String key) { return stack.getTagElement(key); }
    public static CompoundTag editableItemTag(ItemStack stack, String key) { return stack.getOrCreateTagElement(key); }
    public static void storeItemTag(ItemStack stack, String key, CompoundTag tag) { stack.getOrCreateTag().put(key, tag); }
    public static CompoundTag saveEntity(BlockEntity entity, ServerLevel level) { return entity.saveWithFullMetadata(); }
    public static BlockEntity createEntity(BlockPos pos, BlockState state, CompoundTag tag, ServerLevel level) { return BlockEntity.loadStatic(pos, state, tag); }
    public static void loadEntity(BlockEntity entity, CompoundTag tag, ServerLevel level) { entity.load(tag); }
    public static <T extends SavedData> T savedData(MinecraftServer server, Function<CompoundTag, T> load, Supplier<T> create, String id) {
        return server.overworld().getDataStorage().computeIfAbsent(load, create, id);
    }
}
