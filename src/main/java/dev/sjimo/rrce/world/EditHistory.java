package dev.sjimo.rrce.world;

import java.util.LinkedHashMap;
import java.util.Map;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.sjimo.rrce.UserFacingException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** In-memory, force-restore snapshot. Deliberately never serialized to disk. */
public final class EditHistory {
    public record Cell(BlockState state, CompoundTag entity) {
        static Cell take(ServerLevel level, BlockPos pos) {
            BlockEntity be = level.getBlockEntity(pos);
            return new Cell(level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata().copy());
        }
    }

    private final Map<BlockPos, Cell> before = new LinkedHashMap<>();
    private final Map<BlockPos, Cell> after = new LinkedHashMap<>();
    private final String dimension;

    public EditHistory(ServerLevel level) {
        dimension = level.dimension().location().toString();
    }

    public void captureBefore(ServerLevel level, BlockPos pos) {
        before.computeIfAbsent(pos.immutable(), ignored -> Cell.take(level, pos));
    }

    public void captureAfter(ServerLevel level) {
        for (BlockPos pos : before.keySet()) after.put(pos, Cell.take(level, pos));
    }

    public int size() { return before.size(); }

    public void undo(ServerLevel level) { restore(level, before); }
    public void redo(ServerLevel level) { restore(level, after); }

    private void restore(ServerLevel level, Map<BlockPos, Cell> cells) {
        if (!level.dimension().location().toString().equals(dimension))
            throw new UserFacingException("error.rrce.history_dimension", dimension);
        for (BlockPos pos : cells.keySet())
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos))
                throw new UserFacingException("error.rrce.history_position_unavailable", pos.toShortString());
        // Preserve block entities whose block state is unchanged. Removing those first would
        // prevent Minecraft from recreating them when setBlock sees an identical state.
        for (Map.Entry<BlockPos, Cell> entry : cells.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState target = entry.getValue().state();
            if (level.getBlockState(pos).equals(target)) continue;
            if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
            if (!level.setBlock(pos, target, 3))
                throw new UserFacingException("error.rrce.cannot_restore_block", pos.toShortString());
        }
        for (Map.Entry<BlockPos, Cell> entry : cells.entrySet()) {
            CompoundTag tag = entry.getValue().entity();
            BlockEntity be = level.getBlockEntity(entry.getKey());
            if (tag != null) {
                if (be == null) {
                    be = BlockEntity.loadStatic(entry.getKey(), entry.getValue().state(), tag.copy());
                    if (be == null)
                        throw new UserFacingException("error.rrce.cannot_restore_entity", entry.getKey().toShortString());
                    level.setBlockEntity(be);
                }
                be.load(tag.copy());
                be.setChanged();
                level.sendBlockUpdated(entry.getKey(), entry.getValue().state(), entry.getValue().state(), 3);
            }
            if (entry.getValue().state().getBlock() instanceof com.simibubi.create.content.trains.track.TrackBlock)
                level.scheduleTick(entry.getKey(), entry.getValue().state().getBlock(), 1);
        }
        for (BlockPos pos : cells.keySet())
            if (level.getBlockEntity(pos) instanceof TrackBlockEntity track) track.lazyTick();
    }
}
