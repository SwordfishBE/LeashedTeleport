package net.leashedteleport.mixin;

import net.leashedteleport.handler.LeashTeleportHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {

    @Inject(
        method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD")
    )
    private void leashedteleport_captureTransitionPreTeleport(
            TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        if (transition.asPassenger()) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        LeashTeleportHandler.capturePendingTeleport(self);
    }

    @Inject(
        method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("RETURN")
    )
    private void leashedteleport_handleTransitionPostTeleport(
            TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ServerPlayer result = cir.getReturnValue();
        if (result == null) {
            LeashTeleportHandler.clearPendingTeleport(self);
            return;
        }

        LeashTeleportHandler.schedulePendingTeleport(
                result,
                transition.newLevel(),
                transition.position().x,
                transition.position().y,
                transition.position().z
        );
    }

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD")
    )
    private void leashedteleport_capturePreTeleport(
            ServerLevel level, double x, double y, double z,
            Set<Relative> relativeMovements, float yRot, float xRot, boolean isPortalCause,
            CallbackInfoReturnable<Boolean> cir) {

        ServerPlayer self = (ServerPlayer) (Object) this;
        LeashTeleportHandler.capturePendingTeleport(self);
    }

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("RETURN")
    )
    private void leashedteleport_handlePostTeleport(
            ServerLevel level, double x, double y, double z,
            Set<Relative> relativeMovements, float yRot, float xRot, boolean isPortalCause,
            CallbackInfoReturnable<Boolean> cir) {

        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!cir.getReturnValue()) {
            LeashTeleportHandler.clearPendingTeleport(self);
            return;
        }

        LeashTeleportHandler.handlePendingTeleport(self, level, x, y, z);
    }
}
