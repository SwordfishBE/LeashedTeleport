package net.leashedteleport.safety;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TeleportSafetyCheckerTest {
    private ServerLevel level;
    private Mob mob;
    private final Vec3 destination = new Vec3(0.5, 64, 0.5);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void setup() {
        level = mock(ServerLevel.class);
        mob = mock(Mob.class);
        when(level.getMinY()).thenReturn(-64);
        when(level.getHeight()).thenReturn(384);
        when(level.getMaxY()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
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
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() < 64 || pos.getY() >= 66 || pos.getX() != 0
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
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
        when(level.getChunkSource().getChunkNow(anyInt(), anyInt())).thenReturn(null);
        assertNull(TeleportSafetyChecker.findSafeLocation(level, mob, 0.5, 64, 0.5));
        assertFalse(TeleportSafetyChecker.areSearchChunksLoaded(level, mob, destination));
        verify(level, never()).getBlockState(any());
    }

    @Test void ticketReadinessDoesNotRejectReadableTerrain() {
        when(level.hasChunkAt(any())).thenReturn(false);
        assertTrue(TeleportSafetyChecker.isSafeLocation(level, mob, destination));
    }

    @Test void lowerSlabProvidesFractionalSupport() {
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.getY() == 63) return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            return pos.getY() < 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
        assertEquals(new Vec3(0.5, 63.5, 0.5), TeleportSafetyChecker.findSafeLocation(level, mob, 0.5, 64, 0.5));
    }

    @Test void negativeFractionalDestinationInDryGrassIsAccepted() {
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() < 64 ? Blocks.STONE.defaultBlockState()
                    : pos.getY() == 64 ? Blocks.SHORT_DRY_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
        Vec3 target = new Vec3(-4096.3, 64, 153.3);
        assertEquals(target, TeleportSafetyChecker.findSafeLocation(level, mob, target.x, target.y, target.z));
    }

    private void narrowPassage(Integer blockX, Integer blockZ) {
        when(mob.getBoundingBox()).thenReturn(new AABB(-0.45, 0, -0.45, 0.45, 1.4, 0.45));
        doAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() < 64 || (blockX != null && pos.getX() != blockX)
                    || (blockZ != null && pos.getZ() != blockZ)
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
    }

    @Test void fallbackFindsCorridorCenterOnX() {
        narrowPassage(0, null);
        assertEquals(new Vec3(0.5, 64, 0.5), TeleportSafetyChecker.findSafeLocation(level, mob, 0.3, 64, 0.5));
    }

    @Test void fallbackFindsCorridorCenterOnZ() {
        narrowPassage(null, 0);
        assertEquals(new Vec3(0.5, 64, 0.5), TeleportSafetyChecker.findSafeLocation(level, mob, 0.5, 64, 0.3));
    }

    @Test void fallbackCanCenterBothAxes() {
        narrowPassage(0, 0);
        assertEquals(new Vec3(0.5, 64, 0.5), TeleportSafetyChecker.findSafeLocation(level, mob, 0.3, 64, 0.3));
    }

    @Test void fallbackCentersNegativeCoordinatesCorrectly() {
        narrowPassage(-1, null);
        assertEquals(new Vec3(-0.5, 64, 0.5), TeleportSafetyChecker.findSafeLocation(level, mob, -0.7, 64, 0.5));
    }

    @Test void centeredFallbackStillRejectsHazards() {
        narrowPassage(0, 0);
        when(level.getBlockState(new BlockPos(0, 63, 0))).thenReturn(Blocks.MAGMA_BLOCK.defaultBlockState());
        assertNull(TeleportSafetyChecker.findSafeLocation(level, mob, 0.3, 64, 0.3));
    }
}
