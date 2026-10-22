package net.leashedteleport.handler;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat.TeleportType;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.mixin.LeashableEntityAccessor;
import net.leashedteleport.permission.PermissionManager;
import net.leashedteleport.safety.TeleportSafetyChecker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class LeashTeleportHandlerTest {
    private MockedStatic<FabricLoader> loaderStatic;
    private MockedStatic<LeashedTeleportConfig> configs;
    private MockedStatic<PermissionManager> permissions;
    private MockedStatic<TeleportSafetyChecker> safety;
    private LeashedTeleportConfig config;
    private ServerPlayer player;
    private ServerLevel origin;
    private Mob mob;
    private MinecraftServer deferredServer;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LeashTeleportHandler.registerEvents();
    }

    @BeforeEach void setup() {
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(Path.of("build", "test-config"));
        when(loader.getModContainer(anyString())).thenReturn(Optional.empty());
        loaderStatic = mockStatic(FabricLoader.class);
        loaderStatic.when(FabricLoader::getInstance).thenReturn(loader);
        config = new LeashedTeleportConfig();
        config.respectOpenPartiesAndClaims = false;
        configs = mockStatic(LeashedTeleportConfig.class);
        configs.when(LeashedTeleportConfig::get).thenReturn(config);
        permissions = mockStatic(PermissionManager.class);
        permissions.when(() -> PermissionManager.canUse(any())).thenReturn(true);
        permissions.when(() -> PermissionManager.canCrossDimensionTeleport(any())).thenReturn(true);
        permissions.when(() -> PermissionManager.canEnderPearlTeleport(any())).thenReturn(true);
        permissions.when(() -> PermissionManager.canChorusFruitTeleport(any())).thenReturn(true);
        safety = mockStatic(TeleportSafetyChecker.class);
        safety.when(() -> TeleportSafetyChecker.findSafeLocation(any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(call -> new Vec3((double) call.getArgument(2), (double) call.getArgument(3), (double) call.getArgument(4)));
        player = mock(ServerPlayer.class);
        origin = mock(ServerLevel.class);
        mob = mock(Mob.class, withSettings().extraInterfaces(LeashableEntityAccessor.class));
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(mob.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(origin);
        when(player.getBoundingBox()).thenReturn(new AABB(999, 64, 1999, 1001, 66, 2001));
        when(player.getX()).thenReturn(1000.0);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(2000.0);
        when(mob.level()).thenReturn(origin);
        when(mob.getLeashHolder()).thenReturn(player);
        doReturn(mock(EntityType.class)).when(mob).getType();
        when(origin.getEntitiesOfClass(eq(Mob.class), any())).thenReturn(List.of(mob));
        LeashTeleportHandler.clearAllPending();
    }

    @AfterEach void close() {
        if (deferredServer != null) PendingLeashTeleports.clear(deferredServer);
        LeashTeleportHandler.clearAllPending();
        safety.close();
        permissions.close();
        configs.close();
        loaderStatic.close();
    }

    @Test void disabledPearlCannotBecomeGeneralTeleport() {
        config.ender_pearl_teleport = false;
        LeashTeleportHandler.capturePendingTeleport(player, TeleportType.ENDER_PEARL);
        LeashTeleportHandler.capturePendingTeleport(player);
        LeashTeleportHandler.handlePendingTeleport(player);
        safety.verifyNoInteractions();
    }

    @Test void deniedPearlPermissionCannotBecomeGeneralTeleport() {
        permissions.when(() -> PermissionManager.canEnderPearlTeleport(player)).thenReturn(false);
        LeashTeleportHandler.capturePendingTeleport(player, TeleportType.ENDER_PEARL);
        LeashTeleportHandler.capturePendingTeleport(player);
        LeashTeleportHandler.handlePendingTeleport(player);
        safety.verifyNoInteractions();
    }

    @Test void disabledChorusCannotBecomeGeneralTeleport() {
        config.chorus_fruit_teleport = false;
        LeashTeleportHandler.capturePendingTeleport(player, TeleportType.CHORUS_FRUIT);
        LeashTeleportHandler.capturePendingTeleport(player);
        LeashTeleportHandler.handlePendingTeleport(player);
        safety.verifyNoInteractions();
    }

    @Test void actualDestinationIsUsedAndPendingIsConsumedOnce() {
        LeashTeleportHandler.capturePendingTeleport(player);
        when(player.getX()).thenReturn(1024.0);
        LeashTeleportHandler.handlePendingTeleport(player);
        LeashTeleportHandler.handlePendingTeleport(player);
        verify(mob, times(1)).snapTo(1024, 64, 2000, 0, 0);
    }

    @Test void disconnectCleanupRemovesBothKindsOfPendingState() throws Exception {
        LeashTeleportHandler.capturePendingTeleport(player);
        pendingLeashes().put(mob.getUUID(), player.getUUID());
        UUID otherMob = UUID.randomUUID();
        pendingLeashes().put(otherMob, UUID.randomUUID());
        ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        ServerGamePacketListenerImpl.class.getField("player").set(connection, player);
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(connection, null);
        LeashTeleportHandler.handlePendingTeleport(player);
        safety.verifyNoInteractions();
        assertEquals(Set.of(otherMob), pendingLeashes().keySet());
    }

    @Test void serverStopAndTickEndDiscardInterruptedOperations() throws Exception {
        LeashTeleportHandler.capturePendingTeleport(player);
        pendingLeashes().put(mob.getUUID(), player.getUUID());
        ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(null);
        assertTrue(pendingLeashes().isEmpty());
        LeashTeleportHandler.handlePendingTeleport(player);
        LeashTeleportHandler.capturePendingTeleport(player);
        ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(null);
        LeashTeleportHandler.handlePendingTeleport(player);
        safety.verifyNoInteractions();
    }

    @Test void absentOpacAllowsTeleport() {
        assertTrue(OpenPartiesAndClaimsCompat.canTeleportInto(origin, BlockPos.ZERO, player, TeleportType.GENERAL));
    }

    @Test void installedOpacWithMissingApiBlocksTeleport() {
        when(FabricLoader.getInstance().isModLoaded("openpartiesandclaims")).thenReturn(true);
        assertFalse(OpenPartiesAndClaimsCompat.canTeleportInto(origin, BlockPos.ZERO, player, TeleportType.GENERAL));
    }

    @Test void failedDimensionTransferDropsLeashInsteadOfReattachingAcrossWorlds() throws Exception {
        LeashTeleportHandler.capturePendingTeleport(player);
        when(player.level()).thenReturn(mock(ServerLevel.class));
        LeashTeleportHandler.handlePendingTeleport(player);
        verify(mob, atLeastOnce()).dropLeash();
        verify(mob, never()).setLeashedTo(any(), anyBoolean());
        assertTrue(pendingLeashes().isEmpty());
    }

    @Test void throwingDimensionTransferStillClearsPendingLeash() throws Exception {
        LeashTeleportHandler.capturePendingTeleport(player);
        when(player.level()).thenReturn(mock(ServerLevel.class));
        when(mob.teleportTo(any(), anyDouble(), anyDouble(), anyDouble(), anySet(), anyFloat(), anyFloat(), anyBoolean()))
                .thenThrow(new IllegalStateException("simulated cancelled transfer"));
        assertThrows(IllegalStateException.class, () -> LeashTeleportHandler.handlePendingTeleport(player));
        verify(mob, atLeastOnce()).dropLeash();
        verify(mob, never()).setLeashedTo(any(), anyBoolean());
        assertTrue(pendingLeashes().isEmpty());
    }

    private void prepareColdDestination() {
        deferredServer = mock(MinecraftServer.class);
        PlayerList players = mock(PlayerList.class);
        when(origin.getServer()).thenReturn(deferredServer);
        when(origin.dimension()).thenReturn(Level.OVERWORLD);
        when(deferredServer.getLevel(Level.OVERWORLD)).thenReturn(origin);
        when(deferredServer.getPlayerList()).thenReturn(players);
        when(players.getPlayer(player.getUUID())).thenReturn(player);
        when(origin.getEntity(mob.getUUID())).thenReturn(mob);
        when(player.isAlive()).thenReturn(true);
        when(mob.isAlive()).thenReturn(true);
        when(player.position()).thenReturn(new Vec3(1000, 64, 2000));
        when(mob.position()).thenReturn(new Vec3(0, 64, 0));
        var holder = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.Entity>(player);
        when(mob.getLeashHolder()).thenAnswer(call -> holder.get());
        doAnswer(call -> { holder.set(null); return null; }).when(mob).removeLeash();
        doAnswer(call -> { holder.set(call.getArgument(0)); return null; }).when(mob).setLeashedTo(any(), anyBoolean());
        safety.when(() -> TeleportSafetyChecker.findSafeLocation(any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(null);
        LeashTeleportHandler.capturePendingTeleport(player);
        when(mob.distanceToSqr(player)).thenReturn(1000000.0);
        LeashTeleportHandler.handlePendingTeleport(player);
        verify(mob).removeLeash();
        verify(player, never()).sendSystemMessage(any());
    }

    @Test void coldDestinationRetriesWhenTerrainBecomesReadable() {
        prepareColdDestination();
        when(deferredServer.getTickCount()).thenReturn(5);
        safety.when(() -> TeleportSafetyChecker.findSafeLocation(any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new Vec3(1000, 64, 2000));
        PendingLeashTeleports.tick(deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).snapTo(1000, 64, 2000, 0, 0);
        verify(mob, times(1)).setLeashedTo(player, true);
        verify(mob, never()).spawnAtLocation(eq(origin), eq(Items.LEAD));
    }

    @Test void coldDestinationTimeoutReturnsOneLead() {
        prepareColdDestination();
        when(deferredServer.getTickCount()).thenReturn(100);
        PendingLeashTeleports.tick(deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(player).sendSystemMessage(any());
    }

    @Test void disconnectCancelsColdDestination() throws Exception {
        prepareColdDestination();
        ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        ServerGamePacketListenerImpl.class.getField("player").set(connection, player);
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(connection, deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
    }

    @Test void walkingAwayCancelsColdDestination() {
        prepareColdDestination();
        when(player.position()).thenReturn(new Vec3(1010, 64, 2000));
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
    }

    @Test void secondTeleportCancelsColdDestination() {
        prepareColdDestination();
        LeashTeleportHandler.capturePendingTeleport(player);
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
    }

    @Test void unloadingSourceMobReturnsDetachedLeadEvenIfRemoved() {
        prepareColdDestination();
        when(mob.isRemoved()).thenReturn(true);
        PendingLeashTeleports.cancelMob(mob);
        PendingLeashTeleports.cancelMob(mob);
        verify(mob, never()).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(origin, never()).addFreshEntity(any());
        try (var stacks = mockConstruction(net.minecraft.world.item.ItemStack.class);
             var items = mockConstruction(net.minecraft.world.entity.item.ItemEntity.class)) {
            PendingLeashTeleports.tick(deferredServer);
            PendingLeashTeleports.tick(deferredServer);
            assertEquals(1, items.constructed().size());
            verify(origin, times(1)).addFreshEntity(items.constructed().getFirst());
            verify(items.constructed().getFirst()).setDefaultPickUpDelay();
        }
    }

    @Test void serverStopClearsColdDestination() {
        prepareColdDestination();
        ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
    }

    @Test void reLeashingByAnotherPlayerRefundsOriginalLeadOnce() {
        prepareColdDestination();
        ServerPlayer other = mock(ServerPlayer.class);
        mob.setLeashedTo(other, true);
        PendingLeashTeleports.tick(deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        PendingLeashTeleports.cancelPlayer(deferredServer, player.getUUID());
        assertSame(other, mob.getLeashHolder());
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(mob, times(1)).setLeashedTo(other, true);
        verify(mob, never()).dropLeash();
    }

    @Test void reLeashingByOriginalPlayerRefundsOriginalLeadOnce() {
        prepareColdDestination();
        mob.setLeashedTo(player, true);
        PendingLeashTeleports.tick(deferredServer);
        PendingLeashTeleports.tick(deferredServer);
        assertSame(player, mob.getLeashHolder());
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(mob, times(1)).setLeashedTo(player, true);
        verify(mob, never()).dropLeash();
    }

    @Test void disconnectAfterReLeashingRefundsOriginalLeadOnce() {
        prepareColdDestination();
        ServerPlayer other = mock(ServerPlayer.class);
        mob.setLeashedTo(other, true);
        PendingLeashTeleports.cancelPlayer(deferredServer, player.getUUID());
        PendingLeashTeleports.tick(deferredServer);
        assertSame(other, mob.getLeashHolder());
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(mob, never()).dropLeash();
    }

    @Test void unloadingReLeashedMobQueuesOriginalLeadRefundOnce() {
        prepareColdDestination();
        ServerPlayer other = mock(ServerPlayer.class);
        mob.setLeashedTo(other, true);
        when(mob.isRemoved()).thenReturn(true);
        PendingLeashTeleports.cancelMob(mob);
        PendingLeashTeleports.cancelMob(mob);
        verify(origin, never()).addFreshEntity(any());
        try (var stacks = mockConstruction(net.minecraft.world.item.ItemStack.class);
             var items = mockConstruction(net.minecraft.world.entity.item.ItemEntity.class)) {
            PendingLeashTeleports.tick(deferredServer);
            PendingLeashTeleports.tick(deferredServer);
            assertEquals(1, items.constructed().size());
            verify(origin, times(1)).addFreshEntity(items.constructed().getFirst());
        }
        assertSame(other, mob.getLeashHolder());
        verify(mob, never()).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(mob, never()).dropLeash();
    }

    @Test void replacingPendingTransferAccountsForBothDetachedLeads() {
        prepareColdDestination();
        mob.setLeashedTo(player, true);
        PendingLeashTeleports.schedule(player, mob, TeleportType.GENERAL);
        verify(origin, never()).addFreshEntity(any());
        try (var stacks = mockConstruction(net.minecraft.world.item.ItemStack.class);
             var items = mockConstruction(net.minecraft.world.entity.item.ItemEntity.class)) {
            PendingLeashTeleports.tick(deferredServer);
            PendingLeashTeleports.tick(deferredServer);
            assertEquals(1, items.constructed().size());
            verify(origin, times(1)).addFreshEntity(items.constructed().getFirst());
            when(deferredServer.getTickCount()).thenReturn(100);
            PendingLeashTeleports.tick(deferredServer);
            PendingLeashTeleports.tick(deferredServer);
        }
        verify(mob, times(1)).spawnAtLocation(eq(origin), eq(Items.LEAD));
        verify(mob, times(2)).removeLeash();
        verify(mob, never()).dropLeash();
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> pendingLeashes() throws Exception {
        var field = LeashTeleportHandler.class.getDeclaredField("PENDING_LEASH");
        field.setAccessible(true);
        return (Map<UUID, UUID>) field.get(null);
    }
}
