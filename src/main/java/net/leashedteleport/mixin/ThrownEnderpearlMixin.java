package net.leashedteleport.mixin;

import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat;
import net.leashedteleport.handler.LeashTeleportHandler;
import net.leashedteleport.permission.PermissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {

    @Inject(
            method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
                    shift = At.Shift.BEFORE
            )
    )
    private void leashedteleport$captureEnderPearlTeleport(HitResult result, CallbackInfo ci) {
        if (!LeashedTeleportConfig.get().isEnderPearlTeleport()) {
            return;
        }

        Entity owner = ((ThrownEnderpearl) (Object) this).getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        if (!PermissionManager.canEnderPearlTeleport(player)) {
            return;
        }

        LeashTeleportHandler.capturePendingTeleport(player, OpenPartiesAndClaimsCompat.TeleportType.ENDER_PEARL);
    }
}
