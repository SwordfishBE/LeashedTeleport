package net.leashedteleport.mixin;

import net.leashedteleport.handler.LeashTeleportHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    // teleportTo delegates here, so one hook covers both entry points.
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
    private void leashedteleport_capture(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (!transition.asPassenger()) {
            LeashTeleportHandler.capturePendingTeleport((ServerPlayer) (Object) this);
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("RETURN"))
    private void leashedteleport_complete(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (transition.asPassenger()) return;
        ServerPlayer result = cir.getReturnValue();
        if (result == null) {
            LeashTeleportHandler.clearPendingTeleport((ServerPlayer) (Object) this);
        } else {
            LeashTeleportHandler.handlePendingTeleport(result);
        }
    }
}
