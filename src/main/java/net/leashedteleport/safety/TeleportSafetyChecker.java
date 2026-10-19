package net.leashedteleport.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class TeleportSafetyChecker {
    private static final int SEARCH_RADIUS_HORIZONTAL = 5;
    private static final int SEARCH_RADIUS_VERTICAL = 3;

    private TeleportSafetyChecker() {}

    public static BlockPos findSafeLocation(ServerLevel level, Mob mob, double x, double y, double z) {
        BlockPos target = BlockPos.containing(x, y, z);
        // Search nearby positions first instead of jumping straight to the outer corner.
        for (int radius = 0; radius <= SEARCH_RADIUS_HORIZONTAL; radius++) {
            for (int dy = 0; dy <= SEARCH_RADIUS_VERTICAL; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos above = target.offset(dx, dy, dz);
                        if (isSafeLocation(level, mob, above)) return above;
                        if (dy > 0) {
                            BlockPos below = target.offset(dx, -dy, dz);
                            if (isSafeLocation(level, mob, below)) return below;
                        }
                    }
                }
            }
        }
        return null;
    }

    static boolean isSafeLocation(ServerLevel level, Mob mob, BlockPos pos) {
        AABB box = mob.getBoundingBox().move(pos.getX() + 0.5 - mob.getX(),
                pos.getY() - mob.getY(), pos.getZ() + 0.5 - mob.getZ());
        if (box.minY < level.getMinY() || box.maxY > level.getMinY() + level.getHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;

        // Inspect the complete occupied volume and the floor under the full footprint.
        int minX = Mth.floor(box.minX + 1.0E-7);
        int maxX = Mth.floor(box.maxX - 1.0E-7);
        int minZ = Mth.floor(box.minZ + 1.0E-7);
        int maxZ = Mth.floor(box.maxZ - 1.0E-7);
        int minY = Mth.floor(box.minY) - 1;
        int maxY = Mth.floor(box.maxY - 1.0E-7);
        for (BlockPos block : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            // Avoid generating chunks merely to search for a fallback destination.
            if (!level.hasChunkAt(block)) return false;
            if (isHazard(level.getBlockState(block))) return false;
        }
        if (!level.noCollision(mob, box)) return false;
        BlockPos ground = pos.below();
        BlockState support = level.getBlockState(ground);
        return support.is(Blocks.WATER) || support.isFaceSturdy(level, ground, Direction.UP);
    }

    private static boolean isHazard(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }
}
