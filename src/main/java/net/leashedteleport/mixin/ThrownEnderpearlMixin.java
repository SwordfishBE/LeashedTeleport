package net.leashedteleport.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat;
import net.leashedteleport.handler.LeashTeleportHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Enclose mounted handlers that move the owner as a passenger instead of a general teleport.
@Mixin(value = ThrownEnderpearl.class, priority = 1100)
public class ThrownEnderpearlMixin {
    @WrapOperation(
            method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;")
    )
    private ServerPlayer leashedteleport$handleEnderPearlTeleport(
            ServerPlayer player, TeleportTransition transition, Operation<ServerPlayer> original
    ) {
        LeashTeleportHandler.capturePendingTeleport(player, OpenPartiesAndClaimsCompat.TeleportType.ENDER_PEARL);
        try {
            ServerPlayer result = original.call(player, transition);
            if (result != null) LeashTeleportHandler.handlePendingTeleport(result);
            return result;
        } finally {
            LeashTeleportHandler.clearPendingTeleport(player);
        }
    }
}
