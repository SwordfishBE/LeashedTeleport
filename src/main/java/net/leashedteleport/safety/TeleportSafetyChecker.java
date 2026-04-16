package net.leashedteleport.safety;

import net.leashedteleport.LeashedTeleportMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Checks if a teleport destination is safe for entities.
 * Prevents teleporting into lava, fire, void, or suffocation.
 */
public class TeleportSafetyChecker {

    private static final int SEARCH_RADIUS_HORIZONTAL = 5;
    private static final int SEARCH_RADIUS_VERTICAL = 3;

    /**
     * Find a safe teleport location near the target position.
     * Returns the original position if it's safe, or a nearby safe position if found.
     * Returns null if no safe location could be found.
     */
    public static BlockPos findSafeLocation(ServerLevel level, double x, double y, double z) {
        BlockPos targetPos = BlockPos.containing(x, y, z);
        WorldBorder border = level.getWorldBorder();
        
        // First check if the target position itself is safe
        if (isSafeLocation(level, targetPos, border)) {
            return targetPos;
        }

        LeashedTeleportMod.LOGGER.debug("[{}] Target position {} is unsafe, searching for safe alternative...",
                LeashedTeleportMod.MOD_NAME, targetPos);

        // Search in expanding radius for a safe spot
        for (int dy = 0; dy <= SEARCH_RADIUS_VERTICAL; dy++) {
            for (int dx = -SEARCH_RADIUS_HORIZONTAL; dx <= SEARCH_RADIUS_HORIZONTAL; dx++) {
                for (int dz = -SEARCH_RADIUS_HORIZONTAL; dz <= SEARCH_RADIUS_HORIZONTAL; dz++) {
                    BlockPos candidate = targetPos.offset(dx, dy, dz);
                    if (isSafeLocation(level, candidate, border)) {
                        LeashedTeleportMod.LOGGER.debug("[{}] Found safe location at {} (offset from target: {}, {}, {})",
                            LeashedTeleportMod.MOD_NAME, candidate, dx, dy, dz);
                        return candidate;
                    }
                }
            }
        }

        // Also try searching downward
        for (int dy = -1; dy >= -SEARCH_RADIUS_VERTICAL; dy--) {
            for (int dx = -SEARCH_RADIUS_HORIZONTAL; dx <= SEARCH_RADIUS_HORIZONTAL; dx++) {
                for (int dz = -SEARCH_RADIUS_HORIZONTAL; dz <= SEARCH_RADIUS_HORIZONTAL; dz++) {
                    BlockPos candidate = targetPos.offset(dx, dy, dz);
                    if (isSafeLocation(level, candidate, border)) {
                        LeashedTeleportMod.LOGGER.debug("[{}] Found safe location at {} (offset from target: {}, {}, {})",
                            LeashedTeleportMod.MOD_NAME, candidate, dx, dy, dz);
                        return candidate;
                    }
                }
            }
        }

        LeashedTeleportMod.LOGGER.warn("[{}] No safe location found near {}", LeashedTeleportMod.MOD_NAME, targetPos);
        return null;
    }

    /**
     * Check if a specific position is safe to teleport to.
     */
    private static boolean isSafeLocation(ServerLevel level, BlockPos pos, WorldBorder border) {
        // Check void (below minimum build height)
        if (pos.getY() < level.getMinY()) {
            return false;
        }

        // Check if above maximum build height
        if (pos.getY() > level.getMinY() + level.getHeight() - 2) {
            return false;
        }

        BlockPos feetPos = pos;
        BlockPos headPos = pos.above();
        BlockPos groundPos = pos.below();

        if (!border.isWithinBounds(feetPos) || !border.isWithinBounds(headPos) || !border.isWithinBounds(groundPos)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        BlockState groundState = level.getBlockState(groundPos);

        // Check for lava
        if (isLava(feetState) || isLava(headState) || isLava(groundState)) {
            return false;
        }

        // Check for fire
        if (isFire(feetState) || isFire(headState)) {
            return false;
        }

        // Check for dangerous blocks (cactus, sweet berry bush, etc.)
        if (isDangerous(feetState) || isDangerous(headState)) {
            return false;
        }

        // Check for suffocation (both feet and head positions must be passable)
        if (!isPassable(level, feetPos, feetState) || !isPassable(level, headPos, headState)) {
            return false;
        }

        // Need a solid block to stand on (or liquid that's safe to stand in like water)
        if (!canStandOn(level, groundPos, groundState)) {
            return false;
        }

        return true;
    }

    private static boolean isLava(BlockState state) {
        if (state.is(Blocks.LAVA)) return true;
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && fluidState.is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static boolean isFire(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }

    private static boolean isDangerous(BlockState state) {
        return state.is(Blocks.CACTUS) || 
               state.is(Blocks.SWEET_BERRY_BUSH) ||
               state.is(Blocks.MAGMA_BLOCK) ||
               state.is(Blocks.WITHER_ROSE) ||
               state.is(Blocks.POWDER_SNOW);
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
        // Air and non-solid blocks are passable
        if (state.isAir()) return true;
        
        // Water is passable (entity can swim)
        if (state.is(Blocks.WATER)) return true;
        
        // Blocks with no collision shape, like grass or flowers, are passable.
        if (state.getCollisionShape(level, pos).isEmpty()) return true;
        
        // Most solid blocks are not passable
        return false;
    }

    private static boolean canStandOn(ServerLevel level, BlockPos pos, BlockState state) {
        // Air is not valid ground
        if (state.isAir()) return false;
        
        // Water is acceptable (entity will swim/float)
        if (state.is(Blocks.WATER)) return true;
        
        // A block is safe ground when its top face can firmly support the entity.
        if (state.isFaceSturdy(level, pos, Direction.UP)) return true;
        
        // Some non-solid blocks can be stood on (like slabs, stairs, etc.)
        // but for safety we'll be conservative and only allow solid blocks and water
        return false;
    }
}
