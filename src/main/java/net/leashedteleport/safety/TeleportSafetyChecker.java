package net.leashedteleport.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class TeleportSafetyChecker {
    private static final int HORIZONTAL = 5;
    private static final int VERTICAL = 3;

    private TeleportSafetyChecker() {}

    public static Vec3 findSafeLocation(ServerLevel level, Mob mob, double x, double y, double z) {
        Vec3 target = new Vec3(x, y, z);
        if (isSafeLocation(level, mob, target)) return target;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        Set<Vec3> candidates = new HashSet<>();
        CollisionContext context = CollisionContext.of(mob);
        for (int dx = -HORIZONTAL; dx <= HORIZONTAL; dx++) {
            for (int dz = -HORIZONTAL; dz <= HORIZONTAL; dz++) {
                double px = x + dx, pz = z + dz;
                if (level.getChunkSource().getChunkNow(Mth.floor(px) >> 4, Mth.floor(pz) >> 4) == null) continue;
                for (int dy = -VERTICAL; dy <= VERTICAL; dy++) {
                    addHorizontalCandidates(candidates, target, px, y + dy, pz);
                }
                for (int by = Math.max(level.getMinY(), Mth.floor(y - VERTICAL) - 1);
                     by <= Math.min(level.getMaxY() - 1, Mth.floor(y + VERTICAL)); by++) {
                    BlockPos block = new BlockPos(Mth.floor(px), by, Mth.floor(pz));
                    for (AABB shape : level.getBlockState(block).getCollisionShape(level, block, context).toAabbs()) {
                        double surface = by + shape.maxY;
                        if (Math.abs(surface - y) <= VERTICAL) {
                            addHorizontalCandidates(candidates, target, px, surface, pz);
                        }
                    }
                }
            }
        }
        return candidates.stream().sorted(Comparator.comparingDouble((Vec3 pos) -> pos.distanceToSqr(target))
                .thenComparingDouble(pos -> pos.y).thenComparingDouble(pos -> pos.x).thenComparingDouble(pos -> pos.z))
                .filter(pos -> isSafeLocation(level, mob, pos)).findFirst().orElse(null);
    }

    private static void addHorizontalCandidates(Set<Vec3> candidates, Vec3 target, double x, double y, double z) {
        // A player may fit next to a wall while a wider animal needs the block center.
        for (double px : new double[]{x, Mth.floor(x) + 0.5}) {
            for (double pz : new double[]{z, Mth.floor(z) + 0.5}) {
                if (Math.abs(px - target.x) <= HORIZONTAL && Math.abs(pz - target.z) <= HORIZONTAL) {
                    candidates.add(new Vec3(px, y, pz));
                }
            }
        }
    }

    public static boolean areSearchChunksLoaded(ServerLevel level, Mob mob, Vec3 target) {
        AABB area = bounds(mob, target).inflate(HORIZONTAL, 0, HORIZONTAL);
        for (int cx = Mth.floor(area.minX) >> 4; cx <= Mth.floor(Math.nextDown(area.maxX)) >> 4; cx++) {
            for (int cz = Mth.floor(area.minZ) >> 4; cz <= Mth.floor(Math.nextDown(area.maxZ)) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
            }
        }
        return true;
    }

    private static AABB bounds(Mob mob, Vec3 pos) {
        return mob.getBoundingBox().move(pos.x - mob.getX(), pos.y - mob.getY(), pos.z - mob.getZ());
    }

    static boolean isSafeLocation(ServerLevel level, Mob mob, Vec3 pos) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return false;
        AABB box = bounds(mob, pos);
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        AABB body = box.deflate(1.0E-7);
        AABB support = new AABB(body.minX, pos.y - 0.125, body.minZ, body.maxX, pos.y, body.maxZ);
        int minX = Mth.floor(body.minX), maxX = Mth.floor(body.maxX);
        int minZ = Mth.floor(body.minZ), maxZ = Mth.floor(body.maxZ);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
            }
        }
        boolean supported = false;
        CollisionContext context = CollisionContext.of(mob);
        for (BlockPos block : BlockPos.betweenClosed(minX, Mth.floor(support.minY), minZ,
                maxX, Mth.floor(body.maxY), maxZ)) {
            BlockState state = level.getBlockState(block);
            AABB blockBox = new AABB(block);
            if ((blockBox.intersects(body) || blockBox.intersects(support)) && isHazard(state)) return false;
            if (state.is(Blocks.WATER) && blockBox.intersects(support)) supported = true;
            for (AABB shape : state.getCollisionShape(level, block, context).toAabbs()) {
                AABB worldShape = shape.move(block.getX(), block.getY(), block.getZ());
                if (worldShape.intersects(body)) return false;
                if (worldShape.intersects(support)) supported = true;
            }
        }
        return supported;
    }

    private static boolean isHazard(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.LAVA_CAULDRON)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }
}
