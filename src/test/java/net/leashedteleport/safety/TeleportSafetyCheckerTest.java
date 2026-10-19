package net.leashedteleport.safety;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TeleportSafetyCheckerTest {
    private ServerLevel level;
    private Mob mob;
    private final BlockPos destination = new BlockPos(0, 64, 0);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void setup() {
        level = mock(ServerLevel.class);
        mob = mock(Mob.class);
        when(level.getMinY()).thenReturn(-64);
        when(level.getHeight()).thenReturn(384);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.noCollision(eq(mob), any(AABB.class))).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call ->
                ((BlockPos) call.getArgument(0)).getY() < 64 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        when(mob.getBoundingBox()).thenReturn(new AABB(-0.7, 0, -0.7, 0.7, 2.4, 0.7));
    }

    @Test void openGroundIsAccepted() {
        assertTrue(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
    }

    @Test void magmaUnderAnyPartOfFootprintIsRejected() {
        when(level.getBlockState(new BlockPos(1, 63, 0))).thenReturn(Blocks.MAGMA_BLOCK.defaultBlockState());
        assertFalse(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
    }

    @Test void tallWideAnimalMustFitEntireCollisionBox() {
        when(level.noCollision(eq(mob), any(AABB.class))).thenAnswer(call -> {
            AABB box = call.getArgument(1);
            return box.maxY <= 66 && box.minX >= 0 && box.maxX <= 1;
        });
        assertFalse(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
        when(mob.getBoundingBox()).thenReturn(new AABB(-0.2, 0, -0.2, 0.2, 0.5, 0.2));
        assertTrue(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
    }

    @Test void wholeBodyMustStayWithinWorldBorder() {
        WorldBorder border = new WorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(1);
        when(level.getWorldBorder()).thenReturn(border);
        assertFalse(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
    }

    @Test void unloadedDestinationIsNotSearched() {
        when(level.hasChunkAt(any())).thenReturn(false);
        assertNull(TeleportSafetyChecker.findSafeLocation(level, mob, 0.5, 64, 0.5));
    }
}
